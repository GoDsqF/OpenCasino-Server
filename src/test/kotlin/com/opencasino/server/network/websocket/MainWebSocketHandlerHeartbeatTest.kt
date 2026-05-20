package com.opencasino.server.network.websocket

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.HeartbeatProperties
import com.opencasino.server.config.PING
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.HandshakeInfo
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

class MainWebSocketHandlerHeartbeatTest {

    private fun newHandler(
        sessionService: WebSocketSessionService = mock(),
        heartbeat: HeartbeatProperties = HeartbeatProperties(
            enabled = true,
            interval = Duration.ofSeconds(30),
            pongTimeout = Duration.ofSeconds(10),
        ),
        scheduler: VirtualTimeScheduler = VirtualTimeScheduler.create(),
    ): MainWebSocketHandler {
        val props = ApplicationProperties(heartbeat = heartbeat)
        return MainWebSocketHandler(sessionService, props).also {
            it.setGameRoomManagementServices(mock<RoomService>())
            it.setPokerRoomManagementService(mock<RoomService>())
            it.overrideHeartbeatSchedulerForTests(scheduler)
        }
    }

    @Test
    fun `heartbeatPings emits Message PING on every interval tick`() {
        val scheduler = VirtualTimeScheduler.create()
        val handler = newHandler(scheduler = scheduler)

        val emitted = CopyOnWriteArrayList<Any>()
        val sub = handler.heartbeatPings(Duration.ofSeconds(30)).subscribe { emitted.add(it) }
        try {
            assertEquals(0, emitted.size)
            scheduler.advanceTimeBy(Duration.ofSeconds(29))
            assertEquals(0, emitted.size, "no ping before the first interval elapses")
            scheduler.advanceTimeBy(Duration.ofSeconds(1))
            assertEquals(1, emitted.size, "first ping at exactly interval=30s")
            scheduler.advanceTimeBy(Duration.ofSeconds(30))
            assertEquals(2, emitted.size, "second ping at 2*interval")

            emitted.forEach { m ->
                assertTrue(m is Message)
                assertEquals(PING, (m as Message).type)
            }
        } finally {
            sub.dispose()
        }
    }

    @Test
    fun `handle closes session with SERVER_ERROR when no inbound within interval plus pong-timeout`() {
        val scheduler = VirtualTimeScheduler.create()
        val sessionService: WebSocketSessionService = mock()
        whenever(sessionService.onActive(any())).thenReturn(Flux.never())

        val handler = newHandler(sessionService = sessionService, scheduler = scheduler)
        val session = stubSession()

        handler.handle(session).subscribe()

        scheduler.advanceTimeBy(Duration.ofSeconds(40).plusMillis(1))

        val captor = ArgumentCaptor.forClass(CloseStatus::class.java)
        verify(session).close(captor.capture())
        assertNotNull(captor.value)
        assertEquals(CloseStatus.SERVER_ERROR.code, captor.value.code)
        assertEquals("pong-timeout", captor.value.reason)
    }

    @Test
    fun `handle does not close when heartbeat is disabled`() {
        val scheduler = VirtualTimeScheduler.create()
        val sessionService: WebSocketSessionService = mock()
        whenever(sessionService.onActive(any())).thenReturn(Flux.never())

        val handler = newHandler(
            sessionService = sessionService,
            heartbeat = HeartbeatProperties(enabled = false),
            scheduler = scheduler,
        )
        val session = stubSession()

        handler.handle(session).subscribe()

        scheduler.advanceTimeBy(Duration.ofMinutes(5))

        verify(session, org.mockito.kotlin.never()).close(any<CloseStatus>())
    }

    private fun stubSession(id: String = "ws-test"): WebSocketSession {
        val session: WebSocketSession = mock()
        val handshake: HandshakeInfo = mock()
        whenever(session.id).thenReturn(id)
        whenever(session.handshakeInfo).thenReturn(handshake)
        whenever(handshake.principal).thenReturn(Mono.empty<Principal>())
        whenever(session.receive()).thenReturn(Flux.never<WebSocketMessage>())
        whenever(session.send(any())).thenReturn(Mono.never())
        whenever(session.close(any<CloseStatus>())).thenReturn(Mono.empty())
        return session
    }
}
