package com.opencasino.server.service.impl

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.GAME_ROOM_JOIN_WAIT
import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.crash.factory.CrashPlayerFactory
import com.opencasino.server.game.crash.model.CrashPlayer
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.user.BalanceLedgerService
import com.opencasino.server.user.User
import com.opencasino.server.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.core.publisher.Mono
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

class CrashRoomServiceImplTest {
    private val handshake: HandshakeInfo = mock()
    private val userRepository: UserRepository = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val randomnessService: RandomnessService = mock()
    private val applicationProperties = ApplicationProperties()

    private lateinit var ws: WebSocketSessionService
    private lateinit var service: CrashRoomServiceImpl
    private val sent = mutableListOf<Pair<PlayerSession, Any>>()

    @BeforeEach
    fun setup() {
        ws = mock()
        sent.clear()
        doAnswer {
            sent.add(it.getArgument<PlayerSession>(0) to it.getArgument<Any>(1))
            Unit
        }.whenever(ws).send(any(), any<Any>())

        service =
            CrashRoomServiceImpl(
                CrashPlayerFactory(),
                applicationProperties,
                VirtualTimeScheduler.create(),
                randomnessService,
                ledgerService,
            )
        ReflectionTestUtils.setField(service, "userRepository", userRepository)
        service.setGameManager(ws)
    }

    private fun session(userId: UUID? = UUID.randomUUID()): PlayerSession {
        val s = PlayerSession(UUID.randomUUID().toString(), handshake)
        if (userId != null) s.principal = Principal { userId.toString() }
        return s
    }

    @Test
    fun `join creates a single room and seats the player with loaded profile`() {
        val userId = UUID.randomUUID()
        whenever(userRepository.findById(userId)).thenReturn(
            Mono.just(User(id = userId, email = "p@x.io", balance = 1234.0, displayName = "alice")),
        )
        val session = session(userId)

        service.addPlayerToWait(session, GameRoomJoinEvent(reconnectKey = null, playerUUID = userId.toString()))

        assertEquals(1, service.getRooms().size)
        assertEquals("Crash", session.serviceId)
        assertEquals(service.getRooms().first().key(), session.roomKey)

        val player = session.player as CrashPlayer
        assertEquals(1234.0, player.balance)
        assertEquals("alice", player.displayName)

        val wait = sent.map { it.second as Message }.first()
        assertEquals(GAME_ROOM_JOIN_WAIT, wait.type)
    }

    @Test
    fun `anonymous join seats guest with zero balance`() {
        val session = session(userId = null)

        service.addPlayerToWait(session, GameRoomJoinEvent(reconnectKey = null, playerUUID = "anon"))

        val player = session.player as CrashPlayer
        assertEquals(0.0, player.balance)
        assertEquals("guest", player.displayName)
    }

    @Test
    fun `last player disconnect ends the room`() {
        val userId = UUID.randomUUID()
        whenever(userRepository.findById(userId)).thenReturn(
            Mono.just(User(id = userId, email = "p@x.io", balance = 100.0, displayName = "bob")),
        )
        val session = session(userId)
        service.addPlayerToWait(session, GameRoomJoinEvent(reconnectKey = null, playerUUID = userId.toString()))
        val room = service.getRooms().first()

        room.onDisconnect(session)

        assertTrue(service.getRooms().isEmpty())
        assertNull(service.getRoomByKey(room.key()).orElse(null))
    }

    @Test
    fun `each join gets its own room`() {
        whenever(userRepository.findById(any())).thenAnswer { inv ->
            val id = inv.getArgument<UUID>(0)
            Mono.just(User(id = id, email = "p@x.io", balance = 10.0, displayName = "p"))
        }
        val a = session()
        val b = session()

        service.addPlayerToWait(a, GameRoomJoinEvent(reconnectKey = null, playerUUID = "a"))
        service.addPlayerToWait(b, GameRoomJoinEvent(reconnectKey = null, playerUUID = "b"))

        assertEquals(2, service.getRooms().size)
        assertTrue(a.roomKey != b.roomKey)
    }

    @Test
    fun `player factory makes a distinct crash player per session`() {
        val s1 = session()
        val s2 = session()
        val factory = CrashPlayerFactory()
        val room = mock<com.opencasino.server.game.crash.room.AbstractCrashGameRoom>()
        val p1 = factory.create(1L, GameRoomJoinEvent(null, "a"), room, s1)
        val p2 = factory.create(2L, GameRoomJoinEvent(null, "b"), room, s2)
        assertTrue(p1 !== p2)
        assertSame(s1, (p1 as CrashPlayer).userSession)
    }
}
