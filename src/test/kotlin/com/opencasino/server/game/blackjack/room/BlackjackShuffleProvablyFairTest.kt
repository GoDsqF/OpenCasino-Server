package com.opencasino.server.game.blackjack.room

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.BLACKJACK_SHUFFLE_GAME_TYPE
import com.opencasino.server.config.BlackjackRoomProperties
import com.opencasino.server.config.PROVABLY_FAIR_REVEAL
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.BlackjackPlayerDecisionEvent
import com.opencasino.server.game.blackjack.map.BlackjackMap
import com.opencasino.server.game.blackjack.model.BlackjackHand
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.network.pack.shared.ShuffleRevealPack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.rng.ShuffleOutcomeProvider
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.impl.BlackjackRoomServiceImpl
import com.opencasino.server.service.shared.BlackjackDecision
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.core.publisher.Mono
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

// R5: provably-fair шуз. Шуз тасуется один раз и обслуживает несколько раздач,
// поэтому reveal serverSeed уходит при выходе шуза из игры (reshuffle), а не каждый
// раунд (раскрытие в середине дало бы вычислить будущие карты).
class BlackjackShuffleProvablyFairTest {
    private val handshake: HandshakeInfo = mock()
    private val roomService: BlackjackRoomServiceImpl = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val appProps = ApplicationProperties()

    private val broadcasts = mutableListOf<Any>()
    private val ws: WebSocketSessionService =
        mock {
            on { sendBroadcast(any<Collection<PlayerSession>>(), any<Any>()) } doAnswer { inv ->
                broadcasts.add(inv.getArgument<Any>(1))
                Unit
            }
        }

    private val randomnessService: RandomnessService =
        mock {
            on { commit(any(), any(), any<ShuffleOutcomeProvider>()) } doAnswer { inv ->
                val provider = inv.getArgument<ShuffleOutcomeProvider>(2)
                Mono.just(
                    RandomnessService.RoundCommit(UUID.randomUUID(), "hash", "salt", provider.fromHmac(ByteArray(32))),
                )
            }
            on { reveal(any()) } doReturn Mono.just("revealedseedhex")
        }

    private fun newRoom(props: BlackjackRoomProperties): BlackjackGameRoom =
        BlackjackGameRoom(
            BlackjackMap(),
            UUID.randomUUID(),
            roomService,
            ws,
            VirtualTimeScheduler.create(),
            appProps.game,
            props,
            ledgerService,
            randomnessService,
        )

    private fun seatStarted(room: BlackjackGameRoom): Pair<PlayerSession, BlackjackPlayer> {
        val session = PlayerSession(UUID.randomUUID().toString(), handshake)
        session.principal = Principal { UUID.randomUUID().toString() }
        val player = BlackjackPlayer(1L, room, session)
        player.balance = 1000.0
        player.hands.clear()
        player.hands.add(BlackjackHand(bet = 50.0))
        session.player = player
        room.map.addPlayer(player)
        room.onRoomCreated(listOf(session))
        room.onRoomStarted()
        return session to player
    }

    private fun playOneRound(
        room: BlackjackGameRoom,
        session: PlayerSession,
        player: BlackjackPlayer,
    ) {
        room.onBet(session, BetEvent(50.0))
        if (player.canAct()) {
            room.onPlayerDecision(session, BlackjackPlayerDecisionEvent(BlackjackDecision.STAND.name))
        }
        repeat(30) { room.update() }
    }

    private fun reveals(): List<ShuffleRevealPack> =
        broadcasts
            .filterIsInstance<Message>()
            .filter { it.type == PROVABLY_FAIR_REVEAL }
            .map { it.data as ShuffleRevealPack }

    @Test
    fun `shoe retirement broadcasts a provably-fair reveal`() {
        whenever(ledgerService.applyDelta(any(), any(), any(), any())).thenReturn(Mono.empty())
        // Один 52-картный шуз; порог так высок, что первая же раздача выводит его
        // ниже порога → reset перетасует шуз и раскроет старый.
        val room = newRoom(BlackjackRoomProperties(deckStacks = 1, reshuffleThreshold = 50))
        val (session, player) = seatStarted(room)

        playOneRound(room, session, player)

        val reveals = reveals()
        assertTrue(reveals.isNotEmpty(), "retiring shoe must broadcast PROVABLY_FAIR_REVEAL")
        assertEquals(BLACKJACK_SHUFFLE_GAME_TYPE, reveals.first().gameType)
        assertEquals("revealedseedhex", reveals.first().revealedServerSeed)
    }

    @Test
    fun `shoe that survives a round does not reveal`() {
        whenever(ledgerService.applyDelta(any(), any(), any(), any())).thenReturn(Mono.empty())
        // Порог=1: шуз не выходит из игры после раздачи → reveal не шлётся.
        val room = newRoom(BlackjackRoomProperties(deckStacks = 1, reshuffleThreshold = 1))
        val (session, player) = seatStarted(room)

        playOneRound(room, session, player)

        assertTrue(reveals().isEmpty(), "persistent shoe must not reveal mid-life")
    }
}
