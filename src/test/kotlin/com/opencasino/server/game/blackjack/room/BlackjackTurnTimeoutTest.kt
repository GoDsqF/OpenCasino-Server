package com.opencasino.server.game.blackjack.room

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.BlackjackRoomProperties
import com.opencasino.server.config.UPDATE
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.BlackjackPlayerDecisionEvent
import com.opencasino.server.game.blackjack.map.BlackjackMap
import com.opencasino.server.game.blackjack.model.BlackjackHand
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.network.pack.blackjack.update.GameUpdatePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.impl.BlackjackRoomServiceImpl
import com.opencasino.server.service.shared.BlackjackDecision
import com.opencasino.server.service.shared.ClientState
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.core.publisher.Mono
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

class BlackjackTurnTimeoutTest {
    private val handshake: HandshakeInfo = mock()
    private val webSocketSessionService: WebSocketSessionService = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val roomService: BlackjackRoomServiceImpl = mock()
    private val appProps = ApplicationProperties()
    private val bjProps: BlackjackRoomProperties = appProps.blackjackRoom

    private var fakeNow: Long = 0L

    private fun newRoom(): BlackjackGameRoom =
        object : BlackjackGameRoom(
            BlackjackMap(),
            UUID.randomUUID(),
            roomService,
            webSocketSessionService,
            VirtualTimeScheduler.create(),
            appProps.game,
            bjProps,
            ledgerService,
        ) {
            override fun currentTimeMillis(): Long = fakeNow
        }

    private fun advance(ms: Long) {
        fakeNow += ms
    }

    private fun newSession(): PlayerSession {
        val s = PlayerSession(UUID.randomUUID().toString(), handshake)
        s.principal = Principal { UUID.randomUUID().toString() }
        return s
    }

    private fun seatStarted(room: BlackjackGameRoom): Pair<PlayerSession, BlackjackPlayer> {
        val session = newSession()
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

    private fun updatesFor(session: PlayerSession): List<GameUpdatePack> {
        val sCaptor = argumentCaptor<PlayerSession>()
        val mCaptor = argumentCaptor<Any>()
        verify(webSocketSessionService, atLeastOnce()).send(sCaptor.capture(), mCaptor.capture())
        return sCaptor.allValues
            .zip(mCaptor.allValues)
            .filter { it.first === session }
            .mapNotNull { it.second as? Message }
            .filter { it.type == UPDATE }
            .mapNotNull { it.data as? GameUpdatePack }
    }

    @Test
    fun `player that runs out the clock is auto-stood`() {
        val room = newRoom()
        val (session, player) = seatStarted(room)
        room.onBet(session, BetEvent(50.0))
        // Натуральный блэкджек (21 с раздачи) разрешает руку сразу — тогда ходить
        // нечего и таймаут не упражняется. Пропускаем такой редкий расклад.
        assumeTrue(player.canAct(), "natural blackjack on deal — no turn to time out")

        room.update() // взводит окно хода
        assertEquals(ClientState.AWAITING_TURN, updatesFor(session).last().clientState)

        advance(bjProps.actionTimeoutMs + 1)
        room.update() // дедлайн истёк → авто-STAND обрабатывается этим же тиком

        assertEquals(BlackjackDecision.STAND, player.lastDecision, "просрочка → авто-STAND")
        assertTrue(player.currentHand().resolved, "рука закрыта авто-STAND-ом")
    }

    @Test
    fun `clientState reaches SHOWDOWN once the hand resolves`() {
        val room = newRoom()
        whenever(ledgerService.applyDelta(any(), any(), any(), any())).thenReturn(Mono.empty())
        val (session, player) = seatStarted(room)
        room.onBet(session, BetEvent(50.0))
        assumeTrue(player.canAct(), "natural blackjack on deal — already resolved")

        room.onPlayerDecision(session, BlackjackPlayerDecisionEvent(BlackjackDecision.STAND.name))
        room.update() // обрабатывает STAND → handConditions выставлены
        room.update() // else-ветка: UPDATE с clientState=SHOWDOWN, затем reset

        assertEquals(
            ClientState.SHOWDOWN,
            updatesFor(session).last().clientState,
            "после расчёта руки получатель видит SHOWDOWN",
        )
    }
}
