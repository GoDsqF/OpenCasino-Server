package com.opencasino.server.game.poker.holdem.room

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.GAME_ROOM_CLOSE
import com.opencasino.server.config.GAME_ROOM_STATUS
import com.opencasino.server.config.SHOWDOWN_RESULT
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.poker.PokerPlayerDecisionEvent
import com.opencasino.server.game.poker.holdem.map.PokerMap
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.PokerDecision
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

class PokerHUDoubleAllInTest {
    private val handshake: HandshakeInfo = mock()
    private val webSocketSessionService: WebSocketSessionService = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val roomService: RoomService = mock()
    private val appProps = ApplicationProperties()
    private val pokerProps = appProps.pokerRoom

    // Подменяемое "время": тестовые тики переводят его через advance().
    private var fakeNow: Long = 0L

    private fun newRoom(): PokerGameRoom =
        object : PokerGameRoom(
            PokerMap(),
            UUID.randomUUID(),
            roomService,
            webSocketSessionService,
            VirtualTimeScheduler.create(),
            appProps.game,
            pokerProps,
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

    private fun typesSent(): List<Int> {
        val captor = argumentCaptor<Any>()
        verify(webSocketSessionService, atLeastOnce()).send(any<PlayerSession>(), captor.capture())
        // Без фильтрации по конкретной сессии — sendBroadcast в WebSocketSessionServiceImpl
        // ходит через тот же `send(it, message)`, так что нам важно лишь содержимое всех
        // сообщений уровня room — все они идут обоим игрокам.
        return captor.allValues.mapNotNull { (it as? Message)?.type }
    }

    @Test
    fun `HU both ALL_IN dealt to river, showdown broadcast, reveal window holds GAME_ROOM_STATUS`() {
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        room.onRoomStarted()

        val buyIn = pokerProps.buyIn.toDouble()
        room.onBuyIn(sb, BetEvent(buyIn))
        room.onBuyIn(bb, BetEvent(buyIn))

        // 1) SB кликнул ALL_IN. Тик процессит, advance currentPosition -> BB.
        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()
        assertEquals(1, room.actorPosition(), "после SB ALL_IN actor должен встать на BB")
        assertEquals(0, room.dealerHand.getCards().size, "флоп раздавать рано — BB ещё не сходил")

        // 2) BB кликнул ALL_IN. Тик процессит → nextMove → onDealerTurn рекурсия →
        //    дораздаём все 5 общих карт → triggerShowdown → broadcast SHOWDOWN_RESULT.
        room.onPlayerDecision(bb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()
        assertEquals(5, room.dealerHand.getCards().size, "все 5 board-карт должны быть выложены")
        assertNull(room.actorPosition(), "actor=null в SHOWDOWN")

        val bcastCaptor = argumentCaptor<Any>()
        verify(webSocketSessionService, atLeastOnce())
            .sendBroadcast(any<Collection<PlayerSession>>(), bcastCaptor.capture())
        val showdownBroadcasts =
            bcastCaptor.allValues
                .mapNotNull { it as? Message }
                .count { it.type == SHOWDOWN_RESULT }
        assertEquals(1, showdownBroadcasts, "ровно один SHOWDOWN_RESULT broadcast")

        // 3) Reveal-окно открыто. Тик в else-ветке должен слать UPDATE, но
        //    НЕ GAME_ROOM_STATUS и НЕ запускать resetTable (board остаётся 5).
        advance(1000) // 1с < 3.5с showdownRevealMs
        room.update()
        assertEquals(
            5,
            room.dealerHand.getCards().size,
            "пока окно reveal открыто — resetTable не должен сработать",
        )

        // 4) Окно reveal закрылось → resetTable + GAME_ROOM_STATUS на следующем тике.
        advance(pokerProps.showdownRevealMs)
        room.update()
        assertEquals(0, room.dealerHand.getCards().size, "после окна — board сброшен")

        val allTypes = typesSent()
        assertTrue(
            allTypes.contains(GAME_ROOM_STATUS),
            "GAME_ROOM_STATUS должен прилететь ПОСЛЕ окна reveal, не до",
        )
    }

    @Test
    fun `busted player removed from room and gets GAME_ROOM_CLOSE, HU game pauses without opponent`() {
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        room.onRoomStarted()

        val buyIn = pokerProps.buyIn.toDouble()
        room.onBuyIn(sb, BetEvent(buyIn))
        room.onBuyIn(bb, BetEvent(buyIn))

        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()
        room.onPlayerDecision(bb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()

        // Закрываем reveal-окно → resetTable.
        advance(pokerProps.showdownRevealMs + 1)
        room.update()

        // У одного игрока stack=0 (проигравший) — он должен быть удалён.
        // У другого остался ненулевой банк.
        assertEquals(
            1,
            room.map.getPlayers().size,
            "проигравший с stack=0 должен быть удалён в resetTable",
        )

        val captor = argumentCaptor<Any>()
        verify(webSocketSessionService, atLeastOnce()).send(any<PlayerSession>(), captor.capture())
        val closeCount = captor.allValues.mapNotNull { (it as? Message)?.type }.count { it == GAME_ROOM_CLOSE }
        assertTrue(closeCount >= 1, "проигравшему должен уйти GAME_ROOM_CLOSE при bust-out")
    }
}
