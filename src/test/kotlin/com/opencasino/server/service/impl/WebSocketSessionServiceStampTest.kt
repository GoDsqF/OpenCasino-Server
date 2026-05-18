package com.opencasino.server.service.impl

import com.opencasino.server.config.MESSAGE
import com.opencasino.server.network.pack.shared.GameMessagePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.shared.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.test.StepVerifier
import reactor.test.scheduler.VirtualTimeScheduler

class WebSocketSessionServiceStampTest {

    private val handshake: HandshakeInfo = mock()
    private lateinit var service: WebSocketSessionServiceImpl

    @BeforeEach
    fun setUp() {
        service = WebSocketSessionServiceImpl().also {
            it.setBlackjackGameRoomManagementService(mock<RoomService>())
            it.setPokerGameRoomManagementService(mock<RoomService>())
            it.overrideSchedulerForTests(VirtualTimeScheduler.create())
        }
    }

    @Test
    fun `send stamps serviceId from session onto outbound Message`() {
        val s = PlayerSession("ws-1", handshake)
        s.serviceId = "Poker"
        val flux = service.onActive(s)

        val message = Message(MESSAGE, GameMessagePack(MessageType.ROOM.type, "hi"))
        service.send(s, message)

        StepVerifier.create(flux)
            .assertNext { emitted ->
                emitted as Message
                assertEquals("Poker", emitted.serviceId)
            }
            .thenCancel()
            .verify()
    }

    @Test
    fun `send does not overwrite an already-set serviceId`() {
        val s = PlayerSession("ws-1", handshake)
        s.serviceId = "Poker"
        val flux = service.onActive(s)

        val message = Message(MESSAGE, GameMessagePack(MessageType.ROOM.type, "hi"))
        message.serviceId = "Blackjack"
        service.send(s, message)

        StepVerifier.create(flux)
            .assertNext { emitted ->
                emitted as Message
                assertEquals("Blackjack", emitted.serviceId)
            }
            .thenCancel()
            .verify()
    }

    @Test
    fun `send leaves serviceId null when session has none`() {
        val s = PlayerSession("ws-1", handshake)
        val flux = service.onActive(s)

        val message = Message(MESSAGE, "anonymous broadcast")
        service.send(s, message)

        StepVerifier.create(flux)
            .assertNext { emitted ->
                emitted as Message
                assertNull(emitted.serviceId)
            }
            .thenCancel()
            .verify()
    }

    @Test
    fun `sendBroadcast(MessageType,String) wraps payload as GameMessagePack`() {
        val s = PlayerSession("ws-1", handshake)
        s.serviceId = "Blackjack"
        val flux = service.onActive(s)

        service.sendBroadcast(MessageType.SYSTEM, "scheduled maintenance")

        StepVerifier.create(flux)
            .assertNext { emitted ->
                emitted as Message
                assertEquals(MESSAGE, emitted.type)
                assertTrue(emitted.data is GameMessagePack)
                val pack = emitted.data as GameMessagePack
                assertEquals(MessageType.SYSTEM.type, pack.messageType)
                assertEquals("scheduled maintenance", pack.message)
            }
            .thenCancel()
            .verify()
    }
}