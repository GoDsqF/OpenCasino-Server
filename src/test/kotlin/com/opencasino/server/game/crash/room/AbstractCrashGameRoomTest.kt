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
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.rng.RngProfile
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.CrashPhase
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.core.publisher.Mono
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

class AbstractCrashGameRoomTest {
    private val handshake: HandshakeInfo = mock()
    private val roomService: RoomService = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val rng: RandomnessService = mock()
    private val props = CrashRoomProperties()
    private val profile = RngProfile(houseEdge = 0.03, maxPayout = 1000.0)

    private lateinit var ws: WebSocketSessionService
    private val sent = mutableListOf<Pair<PlayerSession, Any>>()
    private val broadcasts = mutableListOf<Any>()

    @BeforeEach
    fun setup() {
        ws = mock()
        sent.clear()
        broadcasts.clear()
        doAnswer {
            sent.add(it.getArgument<PlayerSession>(0) to it.getArgument<Any>(1))
            Unit
        }.whenever(ws)
            .send(any(), any<Any>())
        doAnswer {
            broadcasts.add(it.getArgument<Any>(1))
            Unit
        }.whenever(ws)
            .sendBroadcast(any<Collection<PlayerSession>>(), any<Any>())
        doAnswer { Mono.empty<Any>() }.whenever(ledgerService).applyDelta(any(), any(), any(), any())
        whenever(rng.reveal(any())).thenReturn(Mono.just("revealedseedhex"))
    }

    // Тестовая комната с управляемыми часами (single-семантика по умолчанию).
    private class TestCrashRoom(
        scheduler: VirtualTimeScheduler,
        roomService: RoomService,
        ws: WebSocketSessionService,
        rng: RandomnessService,
        props: CrashRoomProperties,
        profile: RngProfile,
        ledger: BalanceLedgerService,
    ) : AbstractCrashGameRoom(UUID.randomUUID(), scheduler, roomService, ws, rng, props, profile, ledger) {
        var clock: Long = 0
        override val maxPlayers = 1
        override val mode = "SINGLE"

        override fun currentTimeMillis(): Long = clock

        override fun nextClientSeed(): String = "test-client-seed"

        fun exposedPhase(): CrashPhase = phase
    }

    private fun stubCommit(crashPoint: Double) {
        whenever(rng.commit(any(), any(), any<CrashOutcomeProvider>())).thenReturn(
            Mono.just(
                RandomnessService.RoundCommit(
                    roundId = UUID.randomUUID(),
                    serverSeedHash = "hash",
                    clientSeed = "test-client-seed",
                    outcome = crashPoint,
                ),
            ),
        )
    }

    private fun newRoom(): TestCrashRoom =
        TestCrashRoom(
            VirtualTimeScheduler.create(),
            roomService,
            ws,
            rng,
            props,
            profile,
            ledgerService,
        )

    private fun seatPlayer(
        room: TestCrashRoom,
        balance: Double = 1000.0,
    ): Pair<PlayerSession, CrashPlayer> {
        val session = PlayerSession(UUID.randomUUID().toString(), handshake)
        session.principal = Principal { UUID.randomUUID().toString() }
        val player = CrashPlayer(1L, session)
        player.balance = balance
        session.player = player
        room.onRoomCreated(listOf(session))
        return session to player
    }

    private fun lastUpdatePack(): CrashGameUpdatePack =
        sent
            .map { it.second as Message }
            .last { it.type == UPDATE }
            .data as CrashGameUpdatePack

    @Test
    fun `bet opens betting window and debits stake`() {
        stubCommit(2.0)
        val room = newRoom()
        val (session, player) = seatPlayer(room)

        room.onBet(session, BetEvent(bet = 100.0))

        assertEquals(CrashPhase.BETTING, room.exposedPhase())
        assertEquals(900.0, player.balance)
        assertTrue(player.boughtIn)
        assertEquals(100.0, player.stake)
    }

    @Test
    fun `betting transitions to running after window closes`() {
        stubCommit(2.0)
        val room = newRoom()
        val (session, _) = seatPlayer(room)
        room.onBet(session, BetEvent(bet = 100.0))

        room.clock = props.bettingWindowMs
        room.run()

        assertEquals(CrashPhase.RUNNING, room.exposedPhase())
    }

    @Test
    fun `manual cashout pays stake times the anchored tick multiplier`() {
        stubCommit(5.0)
        val room = newRoom()
        val (session, player) = seatPlayer(room)
        room.onBet(session, BetEvent(bet = 100.0))

        val start = props.bettingWindowMs
        room.clock = start
        room.run() // -> RUNNING, roundStart = start

        room.clock = start + 3_000
        room.run() // running tick, m(3000) ≈ 1.22
        val pack = lastUpdatePack()
        val seq = pack.tickSeq
        val multiplier = pack.serverMultiplier

        room.onCashout(session, CrashCashoutEvent(lastTickSeq = seq))

        assertEquals(multiplier, player.cashedOutAt)
        assertEquals(900.0 + Math.round(100.0 * multiplier * 100) / 100.0, player.balance)
    }

    @Test
    fun `auto cashout fires at target below crash point`() {
        stubCommit(5.0)
        val room = newRoom()
        val (session, player) = seatPlayer(room)
        room.onBet(session, BetEvent(bet = 100.0, autoCashoutTarget = 1.5))

        val start = props.bettingWindowMs
        room.clock = start
        room.run()

        // Достаточно времени, чтобы m(t) превысил 1.5 (growthRate 1.07 → ~6s).
        room.clock = start + 7_000
        room.run()

        assertEquals(1.5, player.cashedOutAt)
        assertEquals(900.0 + 150.0, player.balance)
    }

    @Test
    fun `crash settles losers and enters cooldown then waiting`() {
        stubCommit(2.0)
        val room = newRoom()
        val (session, player) = seatPlayer(room)
        room.onBet(session, BetEvent(bet = 100.0))

        val start = props.bettingWindowMs
        room.clock = start
        room.run()

        // За точкой обвала (crashPoint 2.0 ≈ 10.2s при growthRate 1.07).
        room.clock = start + 20_000
        room.run()

        assertEquals(CrashPhase.COOLDOWN, room.exposedPhase())
        assertNull(player.cashedOutAt)
        assertEquals(900.0, player.balance) // stake потерян, без доп. кредита
        val result =
            broadcasts
                .map { it as Message }
                .first { it.type == com.opencasino.server.config.CRASH_ROUND_RESULT }
        assertTrue(result.data.toString().contains("LOSE"))

        room.clock = start + 20_000 + props.cooldownMs
        room.run()
        assertEquals(CrashPhase.WAITING, room.exposedPhase())
    }

    @Test
    fun `stale tick seq beyond cap falls back to receive-time multiplier`() {
        stubCommit(10.0)
        val room = newRoom()
        val (session, player) = seatPlayer(room)
        room.onBet(session, BetEvent(bet = 100.0))

        val start = props.bettingWindowMs
        room.clock = start
        room.run() // RUNNING, seq=1 frame at m=1.0

        // Клик с устаревшим seq=1, но now на 3s позже кадра (> cap 400ms).
        room.clock = start + 3_000
        room.onCashout(session, CrashCashoutEvent(lastTickSeq = 1))

        // Назвать старый кадр не помогает «отмотать» — засчитан receive-time m(3000).
        val expected = Math.floor(Math.pow(1.07, 3.0) * 100) / 100.0
        assertEquals(expected, player.cashedOutAt)
    }
}
