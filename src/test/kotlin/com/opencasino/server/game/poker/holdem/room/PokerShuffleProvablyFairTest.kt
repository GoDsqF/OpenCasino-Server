package com.opencasino.server.game.poker.holdem.room

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.POKER_SHUFFLE_GAME_TYPE
import com.opencasino.server.config.PROVABLY_FAIR_REVEAL
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.poker.PokerPlayerDecisionEvent
import com.opencasino.server.game.poker.holdem.map.PokerMap
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.network.pack.shared.ShuffleRevealPack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.rng.ShuffleOutcomeProvider
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.PokerDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

// R5: provably-fair shuffle wiring — раздача гейтится на готовности колоды,
// reveal serverSeed уходит на showdown.
class PokerShuffleProvablyFairTest {
    private val handshake: HandshakeInfo = mock()
    private val roomService: RoomService = mock()
    private val appProps = ApplicationProperties()
    private val pokerProps = appProps.pokerRoom

    private val broadcasts = mutableListOf<Any>()
    private val ws: WebSocketSessionService =
        mock {
            on { sendBroadcast(any<Collection<PlayerSession>>(), any<Any>()) } doAnswer { inv ->
                broadcasts.add(inv.getArgument<Any>(1))
                Unit
            }
        }

    // commit, выводящий реальную перестановку из переданного провайдера.
    private fun immediateRng(): RandomnessService =
        mock {
            on { commit(any(), any(), any<ShuffleOutcomeProvider>()) } doAnswer { inv ->
                val provider = inv.getArgument<ShuffleOutcomeProvider>(2)
                Mono.just(
                    RandomnessService.RoundCommit(UUID.randomUUID(), "hash", "salt", provider.fromHmac(ByteArray(32))),
                )
            }
            on { reveal(any()) } doReturn Mono.just("revealedseedhex")
        }

    private fun newRoom(rng: RandomnessService): PokerGameRoom =
        PokerGameRoom(
            PokerMap(),
            UUID.randomUUID(),
            roomService,
            ws,
            VirtualTimeScheduler.create(),
            appProps.game,
            pokerProps,
            null,
            rng,
        )

    private fun newSession(): PlayerSession {
        val s = PlayerSession(UUID.randomUUID().toString(), handshake)
        s.principal = Principal { UUID.randomUUID().toString() }
        return s
    }

    private fun seatFresh(
        room: PokerGameRoom,
        session: PlayerSession,
        id: Long,
    ): PokerPlayer {
        val player = PokerPlayer(id, room, session)
        player.balance = 100_000.0
        session.player = player
        room.map.addPlayer(player)
        return player
    }

    @Test
    fun `deal is gated until the shuffle commit resolves`() {
        val sink = Sinks.one<RandomnessService.RoundCommit<List<Int>>>()
        val rng: RandomnessService =
            mock {
                on { commit(any(), any(), any<ShuffleOutcomeProvider>()) } doReturn sink.asMono()
            }
        val room = newRoom(rng)
        val s0 = newSession()
        val s1 = newSession()
        val p0 = seatFresh(room, s0, 1L)
        val p1 = seatFresh(room, s1, 2L)
        room.onRoomStarted()

        val buyIn = pokerProps.buyIn.toDouble()
        room.onBuyIn(s0, BetEvent(buyIn))
        room.onBuyIn(s1, BetEvent(buyIn))

        // Колода ещё тасуется (commit не резолвнулся) — карты не сданы.
        assertEquals(0, p0.playerDeck.getCards().size)
        assertEquals(0, p1.playerDeck.getCards().size)

        // Резолвим commit → накладывается перестановка, отложенная раздача доигрывает.
        val provider = ShuffleOutcomeProvider(52)
        sink.tryEmitValue(
            RandomnessService.RoundCommit(UUID.randomUUID(), "hash", "salt", provider.fromHmac(ByteArray(32))),
        )

        assertEquals(2, p0.playerDeck.getCards().size, "hole cards dealt once deck ready")
        assertEquals(2, p1.playerDeck.getCards().size)
    }

    @Test
    fun `showdown broadcasts a provably-fair reveal`() {
        val room = newRoom(immediateRng())
        val sbSession = newSession()
        val bbSession = newSession()
        val sb = seatFresh(room, sbSession, 1L)
        seatFresh(room, bbSession, 2L)
        room.onRoomStarted()

        val buyIn = pokerProps.buyIn.toDouble()
        room.onBuyIn(sbSession, BetEvent(buyIn))
        room.onBuyIn(bbSession, BetEvent(buyIn))

        // HU: SB ходит первым префлоп. Фолд → единственный выживший → showdown.
        room.onPlayerDecision(sbSession, PokerPlayerDecisionEvent(PokerDecision.FOLD.name, null))
        sb.update()

        val reveal =
            broadcasts
                .filterIsInstance<Message>()
                .firstOrNull { it.type == PROVABLY_FAIR_REVEAL }
        assertTrue(reveal != null, "showdown must broadcast PROVABLY_FAIR_REVEAL")
        val pack = reveal!!.data as ShuffleRevealPack
        assertEquals(POKER_SHUFFLE_GAME_TYPE, pack.gameType)
        assertEquals("revealedseedhex", pack.revealedServerSeed)
        assertEquals("hash", pack.serverSeedHash)
    }
}
