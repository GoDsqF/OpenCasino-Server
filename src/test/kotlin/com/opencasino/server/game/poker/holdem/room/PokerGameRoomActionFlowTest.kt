package com.opencasino.server.game.poker.holdem.room

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.PokerRoomProperties
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.poker.PokerPlayerDecisionEvent
import com.opencasino.server.game.poker.holdem.map.PokerMap
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.PokerDecision
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

class PokerGameRoomActionFlowTest {
    private val handshake: HandshakeInfo = mock()
    private val webSocketSessionService: WebSocketSessionService = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val roomService: RoomService = mock()
    private val appProps = ApplicationProperties()
    private val pokerProps: PokerRoomProperties = appProps.pokerRoom
    private val gameProps = appProps.game

    private fun newRoom(): PokerGameRoom =
        PokerGameRoom(
            PokerMap(),
            UUID.randomUUID(),
            roomService,
            webSocketSessionService,
            VirtualTimeScheduler.create(),
            gameProps,
            pokerProps,
            ledgerService,
        )

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

    // Полный рейз должен переоткрывать круг торгов так, чтобы action завершался
    // у самого рейзера (== следующий после рейзера = последний в круге). Без
    // фикса markRaiser действие после рейза могло вернуться обратно на рейзера
    // и дать ему второй ход в том же круге.
    @Test
    fun `HU preflop SB call then BB raise then SB call closes the round, no extra turn for BB`() {
        val room = newRoom()
        val sbSession = newSession()
        val bbSession = newSession()
        val sb = seatFresh(room, sbSession, 1L) // position=0 = SB/button HU
        val bb = seatFresh(room, bbSession, 2L) // position=1 = BB

        // Skip onRoomCreated — it re-runs map.addPlayer which would re-shuffle
        // positions; positions were already pinned (0 = SB, 1 = BB) by seatFresh.
        room.onRoomStarted()

        val buyIn = pokerProps.buyIn.toDouble()
        room.onBuyIn(sbSession, BetEvent(buyIn))
        room.onBuyIn(bbSession, BetEvent(buyIn))
        // initialTurn ran: lastMaxBet=BB=100; sb.currentBet=50, bb.currentBet=100; action on SB.
        assertEquals(0, room.actorPosition())

        // SB calls (50 -> 100).
        room.onPlayerDecision(sbSession, PokerPlayerDecisionEvent(PokerDecision.CALL.name, 50.0))
        sb.update()
        assertEquals(1, room.actorPosition(), "action should advance to BB")

        // BB raises to 300 (additional 200 over its current 100).
        room.onPlayerDecision(bbSession, PokerPlayerDecisionEvent(PokerDecision.RAISE.name, 200.0))
        bb.update()
        assertEquals(0, room.actorPosition(), "raise reopens action; SB must respond")
        assertEquals(300.0, room.lastMaxBet)
        assertEquals(0, room.dealerHand.getCards().size, "still preflop")

        // SB calls the raise (100 -> 300, additional 200).
        room.onPlayerDecision(sbSession, PokerPlayerDecisionEvent(PokerDecision.CALL.name, 200.0))
        sb.update()

        // Round should close → onDealerTurn dealt flop. Without the fix the
        // action would loop back to BB (position=1) and dealerHand would still be empty.
        assertEquals(
            3,
            room.dealerHand.getCards().size,
            "flop should be dealt — round must end after SB calls the raise",
        )
        assertTrue(room.actorPosition() != null, "new round of betting begins on the flop")
    }
}
