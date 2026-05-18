package com.opencasino.server.game.poker.holdem.room

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.PokerRoomProperties
import com.opencasino.server.event.BetEvent
import com.opencasino.server.game.poker.holdem.map.PokerMap
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

class PokerLateEntryTest {

    private val handshake: HandshakeInfo = mock()
    private val webSocketSessionService: WebSocketSessionService = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val roomService: RoomService = mock()
    private val appProps = ApplicationProperties()
    private val pokerProps: PokerRoomProperties = appProps.pokerRoom
    private val gameProps = appProps.game

    private fun newRoom(): PokerGameRoom = PokerGameRoom(
        PokerMap(), UUID.randomUUID(), roomService, webSocketSessionService,
        VirtualTimeScheduler.create(), gameProps, pokerProps, ledgerService,
    )

    private fun newSession(userId: UUID? = UUID.randomUUID()): PlayerSession {
        val s = PlayerSession(UUID.randomUUID().toString(), handshake)
        if (userId != null) s.principal = Principal { userId.toString() }
        return s
    }

    private fun seatInitial(room: PokerGameRoom, id: Long): Pair<PlayerSession, PokerPlayer> {
        val s = newSession()
        val p = PokerPlayer(id, room, s).apply { balance = 5000.0 }
        s.player = p
        room.map.addPlayer(p)
        room.onRoomCreated(listOf(s))
        return s to p
    }

    @Test
    fun `addLatePlayer adds to map and sends settings only to joiner`() {
        val room = newRoom()
        seatInitial(room, 1L)

        val sLate = newSession()
        val pLate = PokerPlayer(99L, room, sLate).apply { balance = 5000.0 }
        sLate.player = pLate

        room.addLatePlayer(sLate)

        assertEquals(2, room.map.getPlayers().size)
        assertTrue(room.map.getPlayers().contains(pLate))
        verify(webSocketSessionService).send(eq(sLate), any<Message>())
    }

    @Test
    fun `late buy-in while game in progress sets folded=true and seats stack`() {
        val room = newRoom()
        val (s1, _) = seatInitial(room, 1L)
        room.onBuyIn(s1, BetEvent(pokerProps.buyIn.toDouble()))
        assertTrue(room.isGameStarted(), "single seated player should flip gameStarted")

        val sLate = newSession()
        val pLate = PokerPlayer(2L, room, sLate).apply { balance = 5000.0 }
        sLate.player = pLate
        room.addLatePlayer(sLate)
        assertFalse(pLate.boughtIn)
        assertFalse(pLate.folded)

        room.onBuyIn(sLate, BetEvent(pokerProps.buyIn.toDouble()))

        assertTrue(pLate.boughtIn)
        assertTrue(pLate.folded, "late buy-in must sit out current round")
        assertEquals(pokerProps.buyIn.toDouble(), pLate.stack)
    }

    @Test
    fun `double buy-in is rejected`() {
        val room = newRoom()
        val (s1, p1) = seatInitial(room, 1L)
        room.onBuyIn(s1, BetEvent(pokerProps.buyIn.toDouble()))
        val stackAfterFirst = p1.stack

        room.onBuyIn(s1, BetEvent(pokerProps.buyIn.toDouble()))

        assertEquals(stackAfterFirst, p1.stack)
        verify(webSocketSessionService).sendBetFailure(eq(s1), any(), any(), anyOrNull())
    }
}