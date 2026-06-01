package com.opencasino.server.game.blackjack.room

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.BlackjackRoomProperties
import com.opencasino.server.game.blackjack.map.BlackjackMap
import com.opencasino.server.game.blackjack.model.BlackjackHand
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.rng.ShuffleOutcomeProvider
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.impl.BlackjackRoomServiceImpl
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.core.publisher.Mono
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

class BlackjackGameRoomReattachTest {
    private val handshake: HandshakeInfo = mock()
    private val webSocketSessionService: WebSocketSessionService = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val randomnessService: RandomnessService =
        mock {
            on { commit(any(), any(), any<ShuffleOutcomeProvider>()) } doAnswer { inv ->
                val provider = inv.getArgument<ShuffleOutcomeProvider>(2)
                Mono.just(
                    RandomnessService.RoundCommit(UUID.randomUUID(), "hash", "salt", provider.fromHmac(ByteArray(32))),
                )
            }
            on { reveal(any()) } doReturn Mono.just("revealedseedhex")
        }
    private val roomService: BlackjackRoomServiceImpl = mock()
    private val appProps = ApplicationProperties()
    private val bjProps: BlackjackRoomProperties = appProps.blackjackRoom

    private fun newRoom(): BlackjackGameRoom =
        BlackjackGameRoom(
            BlackjackMap(),
            UUID.randomUUID(),
            roomService,
            webSocketSessionService,
            VirtualTimeScheduler.create(),
            appProps.game,
            bjProps,
            ledgerService,
            randomnessService,
        )

    private fun newSession(userId: UUID? = UUID.randomUUID()): PlayerSession {
        val s = PlayerSession(UUID.randomUUID().toString(), handshake)
        if (userId != null) s.principal = Principal { userId.toString() }
        return s
    }

    private fun seatPlayer(
        room: BlackjackGameRoom,
        session: PlayerSession,
    ): BlackjackPlayer {
        val player = BlackjackPlayer(1L, room, session)
        player.balance = 1000.0
        player.hands.clear()
        player.hands.add(BlackjackHand(bet = 50.0))
        session.player = player
        room.map.addPlayer(player)
        room.onRoomCreated(listOf(session))
        return player
    }

    @Test
    fun `onReattach swaps session id in room and rebinds player userSession`() {
        val room = newRoom()
        val userId = UUID.randomUUID()
        val oldSession = newSession(userId)
        val player = seatPlayer(room, oldSession)

        val newSession = newSession(userId)
        newSession.player = player
        newSession.roomKey = room.key()
        newSession.serviceId = "Blackjack"

        room.onReattach(oldSession, newSession)

        assertEquals(newSession, player.userSession)
        assertTrue(room.sessions().any { it.id == newSession.id })
        assertFalse(room.sessions().any { it.id == oldSession.id })
    }

    @Test
    fun `default onGraceStart does not auto-settle the hand`() {
        val room = newRoom()
        val player = seatPlayer(room, newSession())
        // Hand is in progress — not resolved yet.
        assertFalse(player.currentHand().resolved)

        room.onGraceStart(player.userSession)

        assertFalse(
            player.currentHand().resolved,
            "BJ grace should freeze the hand without auto-standing",
        )
    }
}
