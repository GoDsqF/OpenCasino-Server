package com.opencasino.server.game.poker.holdem.room

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.SHOWDOWN_RESULT
import com.opencasino.server.config.UPDATE
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.poker.PokerPlayerDecisionEvent
import com.opencasino.server.game.poker.holdem.map.PokerMap
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.network.pack.poker.showdown.PokerShowdownPack
import com.opencasino.server.network.pack.poker.update.GameUpdatePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.ClientState
import com.opencasino.server.service.shared.PokerDecision
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

class PokerTurnTimeoutAndShowdownTest {
    private val handshake: HandshakeInfo = mock()
    private val webSocketSessionService: WebSocketSessionService = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val roomService: RoomService = mock()
    private val appProps = ApplicationProperties()
    private val pokerProps = appProps.pokerRoom

    private var fakeNow: Long = 0L

    private fun newRoom(): PokerGameRoom =
        object : PokerGameRoom(
            PokerMap(),
            UUID.randomUUID(),
            roomService,
            webSocketSessionService,
            VirtualTimeScheduler.create(),
            appProps.game,
            pokerProps,
            ledgerService,
        ) {
            override fun currentTimeMillis(): Long = fakeNow
        }

    private fun advance(ms: Long) {
        fakeNow += ms
    }

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

    // Последние UPDATE-пакеты, адресованные конкретной сессии (в порядке отправки).
    private fun updatesFor(session: PlayerSession): List<GameUpdatePack> {
        val sCaptor = argumentCaptor<PlayerSession>()
        val mCaptor = argumentCaptor<Any>()
        verify(webSocketSessionService, atLeastOnce()).send(sCaptor.capture(), mCaptor.capture())
        return sCaptor.allValues
            .zip(mCaptor.allValues)
            .filter { it.first === session }
            .mapNotNull { it.second as? Message }
            .filter { it.type == UPDATE }
            .mapNotNull { it.data as? GameUpdatePack }
    }

    private fun lastShowdown(): PokerShowdownPack {
        val captor = argumentCaptor<Any>()
        verify(webSocketSessionService, atLeastOnce())
            .sendBroadcast(any<Collection<PlayerSession>>(), captor.capture())
        return captor.allValues
            .mapNotNull { it as? Message }
            .filter { it.type == SHOWDOWN_RESULT }
            .mapNotNull { it.data as? PokerShowdownPack }
            .last()
    }

    private fun startedHuRoom(): Triple<PokerGameRoom, PlayerSession, PlayerSession> {
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        room.onRoomStarted()
        val buyIn = pokerProps.buyIn.toDouble()
        room.onBuyIn(sb, BetEvent(buyIn))
        room.onBuyIn(bb, BetEvent(buyIn))
        return Triple(room, sb, bb)
    }

    @Test
    fun `actor that runs out the clock is auto-folded and the hand advances`() {
        val (room, sb, _) = startedHuRoom()
        val sbPlayer = sb.player as PokerPlayer

        // Тик 1 — взводит таймер на актора (SB, P0, ходит первым на префлопе HU).
        room.update()
        assertEquals(0, room.actorPosition())

        // Истекает дедлайн → следующий тик авто-фолдит SB.
        advance(pokerProps.actionTimeoutMs + 1)
        room.update()

        assertTrue(sbPlayer.folded, "просрочивший ход игрок должен быть авто-сфолжен")
        assertEquals(PokerDecision.FOLD, sbPlayer.lastDecision)
        // HU: SB сфолдил → остаётся один не-сфолдивший → showdown (actor=null).
        assertNull(room.actorPosition(), "после авто-фолда раздача ушла в showdown")
    }

    @Test
    fun `actor with nothing to call is auto-checked, not folded`() {
        val (room, sb, bb) = startedHuRoom()
        val bbPlayer = bb.player as PokerPlayer

        // SB коллирует BB (докидывает bet/2) → ход переходит к BB, которому
        // коллировать уже нечего (toCall=0).
        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.CALL.name, room.bet / 2))
        room.update()
        assertEquals(1, room.actorPosition(), "после CALL ход у BB")

        // Тик взводит таймер на BB, затем дедлайн истекает.
        room.update()
        advance(pokerProps.actionTimeoutMs + 1)
        room.update()

        assertFalse(bbPlayer.folded, "при toCall=0 авто-действие — CHECK, не FOLD")
        assertEquals(PokerDecision.CHECK, bbPlayer.lastDecision)
        assertEquals(3, room.dealerHand.getCards().size, "авто-CHECK закрыл префлоп → выложен флоп")
    }

    @Test
    fun `turnDeadlineEpochMs is actor-start plus timeout while a turn is live, null at showdown`() {
        val (room, sb, bb) = startedHuRoom()

        // Взводим таймер на актора при fakeNow=0.
        room.update()
        val sbUpdate = updatesFor(sb).last()
        assertEquals(
            pokerProps.actionTimeoutMs,
            sbUpdate.turnDeadlineEpochMs,
            "deadline = момент начала хода (0) + actionTimeoutMs",
        )
        assertEquals(ClientState.AWAITING_TURN, sbUpdate.clientState, "актор → AWAITING_TURN")
        assertEquals(ClientState.IN_ROUND, updatesFor(bb).last().clientState, "не-актор в раздаче → IN_ROUND")

        // Уводим в showdown форсированным фолдом SB.
        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.FOLD.name, null))
        room.update()
        assertNull(room.actorPosition())
        val afterShowdown = updatesFor(bb).last()
        assertNull(afterShowdown.turnDeadlineEpochMs, "в showdown актора нет → deadline=null")
        assertEquals(ClientState.SHOWDOWN, afterShowdown.clientState)
    }

    @Test
    fun `boardHighlight aggregates winner hand cards on a contested showdown`() {
        val (room, sb, bb) = startedHuRoom()

        // Оба ALL_IN → дораздача до ривера → вскрытие с revealHands=true.
        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()
        room.onPlayerDecision(bb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()

        val pack = lastShowdown()
        // boardHighlight согласован с entries победителей: объединение их handCards (dedup).
        val winnerIds = pack.pots.flatMap { it.winnerIds }.toSet()
        val expected =
            pack.entries
                .filter { it.id in winnerIds }
                .flatMap { it.handCards ?: emptyList() }
                .distinct()
        assertEquals(expected, pack.boardHighlight)
        assertTrue(pack.boardHighlight.isNotEmpty(), "вскрытие состоялось → highlight непустой")
    }

    @Test
    fun `boardHighlight is empty when the pot is uncontested (no reveal)`() {
        val (room, sb, _) = startedHuRoom()

        // SB фолдит на префлопе → BB забирает банк без вскрытия (revealHands=false).
        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.FOLD.name, null))
        room.update()

        val pack = lastShowdown()
        assertTrue(pack.boardHighlight.isEmpty(), "без вскрытия highlight пустой")
        assertTrue(pack.entries.all { it.handCards == null }, "без вскрытия карт комбинаций нет")
    }
}
