package com.opencasino.server.game.crash.room

import com.opencasino.server.config.CrashRoomProperties
import com.opencasino.server.config.GAME_ROOM_JOIN_SUCCESS
import com.opencasino.server.game.crash.model.CrashPlayer
import com.opencasino.server.network.pack.crash.CrashSettingsPack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.rng.RngProfile
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

class SingleCrashGameRoomTest {
    private val handshake: HandshakeInfo = mock()
    private val roomService: RoomService = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val rng: RandomnessService = mock()
    private val props = CrashRoomProperties()
    private val profile = RngProfile(houseEdge = 0.03, maxPayout = 1000.0)

    private lateinit var ws: WebSocketSessionService
    private val broadcasts = mutableListOf<Any>()

    @BeforeEach
    fun setup() {
        ws = mock()
        broadcasts.clear()
        doAnswer {
            broadcasts.add(it.getArgument<Any>(1))
            Unit
        }.whenever(ws).sendBroadcast(any<Collection<PlayerSession>>(), any<Any>())
    }

    @Test
    fun `room advertises SINGLE mode and seats one player on creation`() {
        val room =
            SingleCrashGameRoom(
                UUID.randomUUID(),
                VirtualTimeScheduler.create(),
                roomService,
                ws,
                rng,
                props,
                profile,
                ledgerService,
            )
        val session = PlayerSession(UUID.randomUUID().toString(), handshake)
        session.principal = Principal { UUID.randomUUID().toString() }
        session.player = CrashPlayer(1L, session)

        room.onRoomCreated(listOf(session))

        assertEquals(1, room.currentPlayersCount())
        val settings =
            broadcasts
                .map { it as Message }
                .first { it.type == GAME_ROOM_JOIN_SUCCESS }
                .data as CrashSettingsPack
        assertEquals("SINGLE", settings.mode)
    }
}
