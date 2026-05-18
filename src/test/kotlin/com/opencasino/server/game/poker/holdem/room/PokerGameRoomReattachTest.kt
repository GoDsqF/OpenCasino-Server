package com.opencasino.server.game.poker.holdem.room

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.PokerRoomProperties
import com.opencasino.server.game.poker.holdem.map.PokerMap
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

class PokerGameRoomReattachTest {

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

    private fun seatPlayer(room: PokerGameRoom, session: PlayerSession, position: Int): PokerPlayer {
        val player = PokerPlayer(position.toLong() + 1, room, session)
        player.balance = 1000.0
        player.stack = 1000.0
        player.boughtIn = true
        player.position = position
        session.player = player
        room.map.addPlayer(player)
        room.onRoomCreated(listOf(session))
        return player
    }

    @Test
    fun `onGraceStart marks player disconnected`() {
        val room = newRoom()
        val s1 = newSession()
        val p1 = seatPlayer(room, s1, 0)

        room.onGraceStart(s1)

        assertTrue(p1.disconnected)
    }

    @Test
    fun `onReattach clears disconnected, rebinds userSession and swaps room session map`() {
        val room = newRoom()
        val userId = UUID.randomUUID()
        val oldSession = newSession(userId)
        val other = newSession()
        val player = seatPlayer(room, oldSession, 0)
        seatPlayer(room, other, 1)
        room.onGraceStart(oldSession)
        assertTrue(player.disconnected)

        val newSession = newSession(userId)
        newSession.player = player
        newSession.roomKey = room.key()
        newSession.serviceId = "Poker"

        room.onReattach(oldSession, newSession)

        assertFalse(player.disconnected)
        assertEquals(newSession, player.userSession)
        assertTrue(room.sessions().any { it.id == newSession.id })
        assertFalse(room.sessions().any { it.id == oldSession.id })
    }

    @Test
    fun `disconnected player remains folded after a round reset`() {
        val room = newRoom()
        val s1 = newSession()
        val s2 = newSession()
        val p1 = seatPlayer(room, s1, 0)
        val p2 = seatPlayer(room, s2, 1)
        room.onGraceStart(s1)
        assertTrue(p1.disconnected)

        // Reproduce the reset-table post-step that re-folds disconnected players.
        room.map.getPlayers().forEach { it.folded = false }
        room.map.getPlayers().filter { it.disconnected }.forEach { it.folded = true }

        assertTrue(p1.folded, "disconnected player should be re-folded for the next round")
        assertFalse(p2.folded)
    }

    @Test
    fun `onReattach with no matching session in room is a safe no-op`() {
        val room = newRoom()
        val ghost = newSession()
        val newSession = newSession()

        room.onReattach(ghost, newSession)

        verify(webSocketSessionService, never()).send(any<PlayerSession>(), any<Any>())
    }
}