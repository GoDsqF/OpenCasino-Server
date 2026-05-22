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

class WebSocketSessionServiceLeaveTest {
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
        service =
            WebSocketSessionServiceImpl().also {
                it.setBlackjackGameRoomManagementService(blackjackService)
                it.setPokerGameRoomManagementService(pokerService)
                it.overrideSchedulerForTests(scheduler)
                it.disconnectGraceMs = graceMs
            }
    }

    private fun session(
        id: String,
        userId: UUID? = null,
    ): PlayerSession {
        val s = PlayerSession(id, handshake)
        if (userId != null) s.principal = Principal { userId.toString() }
        return s
    }

    @Test
    fun `seated player LEAVE calls onPlayerLeave and clears room binding`() {
        val userId = UUID.randomUUID()
        val s = session("ws-1", userId = userId)
        val room: PokerGameRoom = mock()
        val roomKey = UUID.randomUUID()
        s.roomKey = roomKey
        s.serviceId = "Poker"
        whenever(pokerService.getRoomByKey(roomKey)).thenReturn(Optional.of(room))
        whenever(room.sessions()).thenReturn(listOf(s))
        service.onActive(s)

        service.onPlayerLeave(s)

        verify(room).onPlayerLeave(s)
        // WS stays open — only the room-binding fields are cleared so a
        // subsequent JOIN on the same socket starts cleanly.
        assertNull(s.roomKey)
        assertNull(s.serviceId)
        assertNull(s.player)
    }

    @Test
    fun `LEAVE with no room binding is a no-op`() {
        val s = session("ws-1", userId = UUID.randomUUID())
        service.onActive(s)

        service.onPlayerLeave(s)

        verify(pokerService, never()).getRoomByKey(any())
        verify(blackjackService, never()).getRoomByKey(any())
        assertNull(s.roomKey)
    }

    @Test
    fun `LEAVE cancels pending grace from a previous session`() {
        // Сценарий: пользователь дисконнектился с первой вкладки (поставился grace
        // на ws-1), потом из второй вкладки прислал LEAVE. Grace должен сняться,
        // и onDisconnect от первой сессии больше не выстрелит после expiry.
        val userId = UUID.randomUUID()
        val firstOld = session("ws-1", userId = userId)
        val roomKey = UUID.randomUUID()
        firstOld.roomKey = roomKey
        firstOld.serviceId = "Poker"
        val room: PokerGameRoom = mock()
        whenever(pokerService.getRoomByKey(roomKey)).thenReturn(Optional.of(room))
        whenever(room.sessions()).thenReturn(listOf(firstOld))
        service.onActive(firstOld)
        service.onInactive(firstOld)
        verify(room).onGraceStart(firstOld)

        // Вторая сессия с теми же userId/room присылает LEAVE.
        val second = session("ws-2", userId = userId)
        second.roomKey = roomKey
        second.serviceId = "Poker"
        whenever(room.sessions()).thenReturn(listOf(second))
        service.onActive(second)

        service.onPlayerLeave(second)

        verify(room).onPlayerLeave(second)
        scheduler.advanceTimeBy(Duration.ofMillis(graceMs))
        verify(room, never()).onDisconnect(eq(firstOld))
    }

    @Test
    fun `LEAVE from pre-launch wait queue cleans up via room service`() {
        // Аналогично onInactive: session с roomKey, но не в room.sessions() —
        // значит сидит в очереди ожидания, реальной seating ещё не было.
        val userId = UUID.randomUUID()
        val s = session("ws-1", userId = userId)
        val room: PokerGameRoom = mock()
        val roomKey = UUID.randomUUID()
        s.roomKey = roomKey
        s.serviceId = "Poker"
        whenever(pokerService.getRoomByKey(roomKey)).thenReturn(Optional.of(room))
        whenever(room.sessions()).thenReturn(emptyList())
        service.onActive(s)

        service.onPlayerLeave(s)

        verify(pokerService).removePlayerFromWaitQueue(s)
        verify(room, never()).onPlayerLeave(any())
        assertNull(s.roomKey)
    }

    @Test
    fun `LEAVE for blackjack routes through the blackjack room service`() {
        val userId = UUID.randomUUID()
        val s = session("ws-1", userId = userId)
        val room: BlackjackGameRoom = mock()
        val roomKey = UUID.randomUUID()
        s.roomKey = roomKey
        s.serviceId = "Blackjack"
        whenever(blackjackService.getRoomByKey(roomKey)).thenReturn(Optional.of(room))
        whenever(room.sessions()).thenReturn(listOf(s))
        service.onActive(s)

        service.onPlayerLeave(s)

        verify(room).onPlayerLeave(s)
        verify(pokerService, never()).getRoomByKey(any())
    }

    @Test
    fun `LEAVE when room has gone away is still safe and clears bindings`() {
        val userId = UUID.randomUUID()
        val s = session("ws-1", userId = userId)
        val roomKey = UUID.randomUUID()
        s.roomKey = roomKey
        s.serviceId = "Poker"
        whenever(pokerService.getRoomByKey(roomKey)).thenReturn(Optional.empty())
        service.onActive(s)

        service.onPlayerLeave(s)

        assertNull(s.roomKey)
        assertNull(s.serviceId)
    }
}