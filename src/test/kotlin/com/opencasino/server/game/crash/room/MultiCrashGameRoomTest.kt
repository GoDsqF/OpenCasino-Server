package com.opencasino.server.game.crash.room

import com.opencasino.server.config.CrashRoomProperties
import com.opencasino.server.config.UPDATE
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.crash.CrashCashoutEvent
import com.opencasino.server.game.crash.model.CrashPlayer
import com.opencasino.server.network.pack.crash.CrashGameUpdatePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.rng.CrashOutcomeProvider
import com.opencasino.server.rng.ProvablyFairChain
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.rng.RngProfile
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.CrashPhase
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.core.publisher.Mono
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.MessageDigest
import java.security.Principal
import java.util.UUID

class MultiCrashGameRoomTest {
    private val handshake: HandshakeInfo = mock()
    private val roomService: RoomService = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val rng: RandomnessService = mock()
    private val props = CrashRoomProperties()
    private val profile = RngProfile(houseEdge = 0.03, maxPayout = 1000.0)
    private val roundId = UUID.randomUUID()

    private lateinit var ws: WebSocketSessionService
    private val sent = mutableListOf<Pair<PlayerSession, Any>>()

    @BeforeEach
    fun setup() {
        ws = mock()
        sent.clear()
        doAnswer {
            sent.add(it.getArgument<PlayerSession>(0) to it.getArgument<Any>(1))
            Unit
        }.whenever(ws).send(any(), any<Any>())
        doAnswer { Mono.empty<Any>() }.whenever(ledgerService).applyDelta(any(), any(), any(), any())
        whenever(rng.reserve()).thenReturn(Mono.just(RandomnessService.Reservation(roundId, "hash")))
        whenever(rng.reveal(any())).thenReturn(Mono.just("revealedseedhex"))
    }

    private fun stubDerive(crashPoint: Double) {
        whenever(rng.derive(any(), any(), any(), any<CrashOutcomeProvider>())).thenReturn(
            Mono.just(RandomnessService.RoundCommit(roundId, "hash", "client", crashPoint)),
        )
    }

    private class TestMultiCrashRoom(
        scheduler: VirtualTimeScheduler,
        roomService: RoomService,
        ws: WebSocketSessionService,
        rng: RandomnessService,
        props: CrashRoomProperties,
        profile: RngProfile,
        ledger: BalanceLedgerService,
    ) : MultiCrashGameRoom(UUID.randomUUID(), scheduler, roomService, ws, rng, props, profile, ledger) {
        var clock: Long = 0

        override fun currentTimeMillis(): Long = clock

        fun exposedPhase(): CrashPhase = phase
    }

    private fun newRoom(): TestMultiCrashRoom =
        TestMultiCrashRoom(VirtualTimeScheduler.create(), roomService, ws, rng, props, profile, ledgerService)

    private fun seat(
        id: Long,
        balance: Double = 1000.0,
    ): Pair<PlayerSession, CrashPlayer> {
        val session = PlayerSession(UUID.randomUUID().toString(), handshake)
        session.principal = Principal { UUID.randomUUID().toString() }
        val player = CrashPlayer(id, session)
        player.balance = balance
        session.player = player
        return session to player
    }

    private fun lastUpdateTo(session: PlayerSession): CrashGameUpdatePack =
        sent
            .filter { it.first == session }
            .map { it.second as Message }
            .last { it.type == UPDATE }
            .data as CrashGameUpdatePack

    @Test
    fun `empty room idles in WAITING and never reserves a seed`() {
        val room = newRoom()
        room.onRoomCreated(emptyList())

        repeat(5) {
            room.clock += props.loopRate
            room.run()
        }

        assertEquals(CrashPhase.WAITING, room.exposedPhase())
        verify(rng, never()).reserve()
    }

    @Test
    fun `first seated player starts the cadence`() {
        stubDerive(5.0)
        val room = newRoom()
        val (s1, _) = seat(1L)
        room.onRoomCreated(listOf(s1))

        room.clock += props.loopRate
        room.run()

        assertEquals(CrashPhase.BETTING, room.exposedPhase())
        verify(rng).reserve()
    }

    @Test
    fun `outcome is derived at run-start from a bet-derived client seed`() {
        stubDerive(5.0)
        val room = newRoom()
        val (s1, _) = seat(1L)
        val (s2, _) = seat(2L)
        room.onRoomCreated(listOf(s1))
        room.addLatePlayer(s2)

        room.clock += props.loopRate
        room.run() // -> BETTING
        room.onBet(s1, BetEvent(bet = 100.0))
        room.onBet(s2, BetEvent(bet = 50.0))

        room.clock += props.bettingWindowMs
        room.run() // -> RUNNING, derive fires

        val seedCaptor = argumentCaptor<String>()
        verify(rng).derive(any(), any(), seedCaptor.capture(), any<CrashOutcomeProvider>())
        assertEquals(expectedSeed(listOf("1:100.0", "2:50.0")), seedCaptor.firstValue)
        assertEquals(CrashPhase.RUNNING, room.exposedPhase())
    }

    @Test
    fun `shared curve settles cashed-out winner and busted loser`() {
        stubDerive(5.0)
        val room = newRoom()
        val (s1, p1) = seat(1L)
        val (s2, p2) = seat(2L)
        room.onRoomCreated(listOf(s1))
        room.addLatePlayer(s2)

        room.clock += props.loopRate
        room.run()
        room.onBet(s1, BetEvent(bet = 100.0))
        room.onBet(s2, BetEvent(bet = 100.0))

        room.clock += props.bettingWindowMs
        val start = room.clock
        room.run() // -> RUNNING

        room.clock = start + 3_000
        room.run()
        val seq = lastUpdateTo(s1).tickSeq
        room.onCashout(s1, CrashCashoutEvent(lastTickSeq = seq))

        room.clock = start + 30_000
        room.run() // crash past 5.0

        assertEquals(CrashPhase.COOLDOWN, room.exposedPhase())
        assertNotNull(p1.cashedOutAt)
        assertTrue(p1.payout > 0.0)
        assertNull(p2.cashedOutAt)
        assertEquals(900.0, p2.balance) // stake потерян
    }

    @Test
    fun `cooldown reopens a new betting window while players remain`() {
        stubDerive(2.0)
        val room = newRoom()
        val (s1, _) = seat(1L)
        room.onRoomCreated(listOf(s1))

        room.clock += props.loopRate
        room.run() // BETTING
        room.onBet(s1, BetEvent(bet = 100.0))

        room.clock += props.bettingWindowMs
        val start = room.clock
        room.run() // RUNNING

        room.clock = start + 30_000
        room.run() // crash -> COOLDOWN

        room.clock += props.cooldownMs
        room.run() // cooldown complete -> reopen BETTING

        assertEquals(CrashPhase.BETTING, room.exposedPhase())
        verify(rng, org.mockito.kotlin.times(2)).reserve()
    }

    private fun expectedSeed(fingerprints: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(fingerprints.sorted().joinToString("|").toByteArray())
        return ProvablyFairChain.toHex(digest)
    }
}
