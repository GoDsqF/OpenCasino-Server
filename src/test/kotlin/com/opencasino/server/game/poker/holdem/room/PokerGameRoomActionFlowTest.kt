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

    // Регрессия (user-report 2026-05-24): после ре-рейза комната «ломалась».
    // Корень: круг торгов закрывался по сравнению currentPosition с
    // (roundFirstActor-1), без учёта folded/all-in. Когда игрок прямо перед
    // агрессором сфолдил (частый случай: рейзит тот, кто сидит сразу за
    // выбывшим), currentPosition никогда не совпадал с этим маркером и круг
    // не закрывался — оставшихся активных бесконечно просили чекать.
    @Test
    fun `3-max re-raise closes the round even when the player before the raiser folded`() {
        val room = newRoom()
        val s0 = newSession()
        val s1 = newSession()
        val s2 = newSession()
        seatFresh(room, s0, 1L) // position 0 = SB/button
        seatFresh(room, s1, 2L) // position 1 = BB
        val p2 = seatFresh(room, s2, 3L) // position 2 = UTG, ходит первым префлоп
        room.onRoomStarted()

        val buyIn = pokerProps.buyIn.toDouble()
        room.onBuyIn(s0, BetEvent(buyIn))
        room.onBuyIn(s1, BetEvent(buyIn))
        room.onBuyIn(s2, BetEvent(buyIn))
        // initialTurn: lastMaxBet=BB=100; UTG(pos2) ходит первым префлоп.
        assertEquals(2, room.actorPosition())

        // UTG рейзит до 300 (агрессор #1) → roundFirstActor=2, action → SB(pos0).
        room.onPlayerDecision(s2, PokerPlayerDecisionEvent(PokerDecision.RAISE.name, 300.0))
        p2.update()
        assertEquals(0, room.actorPosition(), "после рейза UTG ход у SB")

        // SB фолдит → action на BB(pos1). Теперь игрок ПЕРЕД будущим агрессором
        // (BB) — это SB, и он сфолдил.
        room.onPlayerDecision(s0, PokerPlayerDecisionEvent(PokerDecision.FOLD.name, null))
        room.map.getPlayerById(1L)!!.update()
        assertEquals(1, room.actorPosition(), "после фолда SB ход у BB")

        // BB ре-рейзит до 700 (агрессор #2) → roundFirstActor=1, currentLastPlayer
        // по старой логике = pos0 = SB (folded). Action → UTG(pos2).
        room.onPlayerDecision(s1, PokerPlayerDecisionEvent(PokerDecision.RAISE.name, 600.0))
        room.map.getPlayerById(2L)!!.update()
        assertEquals(2, room.actorPosition(), "ре-рейз BB вернул action на UTG")
        assertEquals(700.0, room.lastMaxBet)
        assertEquals(0, room.dealerHand.getCards().size, "всё ещё префлоп")

        // UTG коллирует ре-рейз. Круг ДОЛЖЕН закрыться (action вернулся к
        // агрессору BB, все ставки уравнены) → раздаётся флоп. До фикса стол
        // замерзал: currentLastPlayer указывал на сфолдившего SB.
        room.onPlayerDecision(s2, PokerPlayerDecisionEvent(PokerDecision.CALL.name, 400.0))
        p2.update()

        assertEquals(
            3,
            room.dealerHand.getCards().size,
            "флоп должен быть раздан — круг обязан закрыться после колла UTG",
        )
        assertTrue(room.actorPosition() != null, "на флопе начинается новый круг торгов")
    }
}
