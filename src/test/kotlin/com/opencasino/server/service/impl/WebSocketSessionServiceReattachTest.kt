package com.opencasino.server.service.impl

import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.poker.holdem.room.PokerGameRoom
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.RoomService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.time.Duration
import java.util.Optional
import java.util.UUID

class WebSocketSessionServiceReattachTest {

    private lateinit var scheduler: VirtualTimeScheduler
    private lateinit var blackjackService: RoomService
    private lateinit var pokerService: RoomService
    private lateinit var service: WebSocketSessionServiceImpl

    private val handshake: HandshakeInfo = mock()
    private val graceMs = 60_000L

    @BeforeEach
    fun setUp() {
        scheduler = VirtualTimeScheduler.create()
        blackjackService = mock()
        pokerService = mock()
        service = WebSocketSessionServiceImpl().also {
            it.setBlackjackGameRoomManagementService(blackjackService)
            it.setPokerGameRoomManagementService(pokerService)
            it.overrideSchedulerForTests(scheduler)
            it.disconnectGraceMs = graceMs
        }
    }

    private fun session(id: String, userId: UUID? = null): PlayerSession {
        val s = PlayerSession(id, handshake)
        if (userId != null) s.principal = Principal { userId.toString() }
        return s
    }

    @Test
    fun `anonymous disconnect with no room is a no-op on rooms`() {
        val s = session("ws-1", userId = null)
        service.onActive(s)

        service.onInactive(s)

        verify(blackjackService, never()).getRoomByKey(any())
        verify(pokerService, never()).getRoomByKey(any())
    }

    @Test
    fun `anonymous disconnect inside a room immediately settles`() {
        val s = session("ws-1", userId = null)
        val room: PokerGameRoom = mock()
        val roomKey = UUID.randomUUID()
        s.roomKey = roomKey
        s.serviceId = "Poker"
        whenever(pokerService.getRoomByKey(roomKey)).thenReturn(Optional.of(room))
        service.onActive(s)

        service.onInactive(s)

        verify(room).onDisconnect(s)
    }

    @Test
    fun `authenticated disconnect schedules grace and calls onGraceStart`() {
        val userId = UUID.randomUUID()
        val s = session("ws-1", userId = userId)
        val room: PokerGameRoom = mock()
        val roomKey = UUID.randomUUID()
        s.roomKey = roomKey
        s.serviceId = "Poker"
        whenever(pokerService.getRoomByKey(roomKey)).thenReturn(Optional.of(room))
        service.onActive(s)

        service.onInactive(s)

        verify(room).onGraceStart(s)
        verify(room, never()).onDisconnect(any())
    }

    @Test
    fun `grace expiry calls onDisconnect when no reattach happened`() {
        val userId = UUID.randomUUID()
        val s = session("ws-1", userId = userId)
        val room: PokerGameRoom = mock()
        val roomKey = UUID.randomUUID()
        s.roomKey = roomKey
        s.serviceId = "Poker"
        whenever(pokerService.getRoomByKey(roomKey)).thenReturn(Optional.of(room))
        service.onActive(s)
        service.onInactive(s)

        scheduler.advanceTimeBy(Duration.ofMillis(graceMs))

        verify(room).onDisconnect(s)
    }

    @Test
    fun `reattach inside grace cancels onDisconnect and swaps sessions in room`() {
        val userId = UUID.randomUUID()
        val oldSession = session("ws-old", userId = userId)
        val roomKey = UUID.randomUUID()
        val room: BlackjackGameRoom = mock()
        oldSession.roomKey = roomKey
        oldSession.serviceId = "Blackjack"
        whenever(blackjackService.getRoomByKey(roomKey)).thenReturn(Optional.of(room))
        service.onActive(oldSession)
        service.onInactive(oldSession)

        val newSession = PlayerSession("ws-new", handshake)
        service.onActive(newSession)
        service.onPrincipalInit(newSession, Principal { userId.toString() })

        verify(room).onReattach(oldSession, newSession)
        assertEquals(roomKey, newSession.roomKey)
        assertEquals("Blackjack", newSession.serviceId)

        scheduler.advanceTimeBy(Duration.ofMillis(graceMs))
        verify(room, never()).onDisconnect(any())
    }

    @Test
    fun `reattach when room is gone falls back to cleanup with no error`() {
        val userId = UUID.randomUUID()
        val oldSession = session("ws-old", userId = userId)
        val roomKey = UUID.randomUUID()
        oldSession.roomKey = roomKey
        oldSession.serviceId = "Poker"
        val room: PokerGameRoom = mock()
        whenever(pokerService.getRoomByKey(roomKey)).thenReturn(Optional.of(room))
        service.onActive(oldSession)
        service.onInactive(oldSession)

        whenever(pokerService.getRoomByKey(roomKey)).thenReturn(Optional.empty())

        val newSession = PlayerSession("ws-new", handshake)
        service.onActive(newSession)
        service.onPrincipalInit(newSession, Principal { userId.toString() })

        verify(room, never()).onReattach(any(), any())
    }

    @Test
    fun `principal init without a pending disconnect is a normal no-op`() {
        val userId = UUID.randomUUID()
        val s = PlayerSession("ws-1", handshake)
        service.onActive(s)

        service.onPrincipalInit(s, Principal { userId.toString() })

        assertNotNull(s.principal)
    }

    @Test
    fun `expiry honors session-id even after second reconnect-then-disconnect for same user`() {
        // Edge case: user A disconnects (grace #1), reconnects, disconnects again (grace #2).
        // The first expiry must not run any callbacks tied to the stale session.
        val userId = UUID.randomUUID()
        val firstOld = session("ws-1", userId = userId)
        val roomKey = UUID.randomUUID()
        firstOld.roomKey = roomKey
        firstOld.serviceId = "Poker"
        val room: PokerGameRoom = mock()
        whenever(pokerService.getRoomByKey(roomKey)).thenReturn(Optional.of(room))
        service.onActive(firstOld)
        service.onInactive(firstOld)

        val second = PlayerSession("ws-2", handshake)
        service.onActive(second)
        service.onPrincipalInit(second, Principal { userId.toString() })
        service.onInactive(second)

        scheduler.advanceTimeBy(Duration.ofMillis(graceMs))

        // Only the second session expires; the first was reattached and cleared.
        verify(room).onDisconnect(eq(second))
        verify(room, never()).onDisconnect(eq(firstOld))
    }
}