package com.opencasino.server.game.poker.holdem.room

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.GAME_ROOM_CLOSE
import com.opencasino.server.config.GAME_ROOM_STATUS
import com.opencasino.server.config.SHOWDOWN_RESULT
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.poker.PokerPlayerDecisionEvent
import com.opencasino.server.game.poker.holdem.map.PokerMap
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.rng.ShuffleOutcomeProvider
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.PokerDecision
import com.opencasino.server.user.BalanceLedgerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.core.publisher.Mono
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.util.UUID

class PokerHUDoubleAllInTest {
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
    private val roomService: RoomService = mock()
    private val appProps = ApplicationProperties()
    private val pokerProps = appProps.pokerRoom

    // Подменяемое "время": тестовые тики переводят его через advance().
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
            randomnessService,
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

    private fun typesSent(): List<Int> {
        val captor = argumentCaptor<Any>()
        verify(webSocketSessionService, atLeastOnce()).send(any<PlayerSession>(), captor.capture())
        // Без фильтрации по конкретной сессии — sendBroadcast в WebSocketSessionServiceImpl
        // ходит через тот же `send(it, message)`, так что нам важно лишь содержимое всех
        // сообщений уровня room — все они идут обоим игрокам.
        return captor.allValues.mapNotNull { (it as? Message)?.type }
    }

    @Test
    fun `HU both ALL_IN dealt to river, showdown broadcast, reveal window holds GAME_ROOM_STATUS`() {
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        room.onRoomStarted()

        val buyIn = pokerProps.buyIn.toDouble()
        room.onBuyIn(sb, BetEvent(buyIn))
        room.onBuyIn(bb, BetEvent(buyIn))

        // 1) SB кликнул ALL_IN. Тик процессит, advance currentPosition -> BB.
        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()
        assertEquals(1, room.actorPosition(), "после SB ALL_IN actor должен встать на BB")
        assertEquals(0, room.dealerHand.getCards().size, "флоп раздавать рано — BB ещё не сходил")

        // 2) BB кликнул ALL_IN. Тик процессит → nextMove → onDealerTurn рекурсия →
        //    дораздаём все 5 общих карт → triggerShowdown → broadcast SHOWDOWN_RESULT.
        room.onPlayerDecision(bb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()
        assertEquals(5, room.dealerHand.getCards().size, "все 5 board-карт должны быть выложены")
        assertNull(room.actorPosition(), "actor=null в SHOWDOWN")

        val bcastCaptor = argumentCaptor<Any>()
        verify(webSocketSessionService, atLeastOnce())
            .sendBroadcast(any<Collection<PlayerSession>>(), bcastCaptor.capture())
        val showdownBroadcasts =
            bcastCaptor.allValues
                .mapNotNull { it as? Message }
                .count { it.type == SHOWDOWN_RESULT }
        assertEquals(1, showdownBroadcasts, "ровно один SHOWDOWN_RESULT broadcast")

        // 3) Reveal-окно открыто. Тик в else-ветке должен слать UPDATE, но
        //    НЕ GAME_ROOM_STATUS и НЕ запускать resetTable (board остаётся 5).
        advance(1000) // 1с < 3.5с showdownRevealMs
        room.update()
        assertEquals(
            5,
            room.dealerHand.getCards().size,
            "пока окно reveal открыто — resetTable не должен сработать",
        )

        // 4) Окно reveal закрылось → resetTable + GAME_ROOM_STATUS на следующем тике.
        advance(pokerProps.showdownRevealMs)
        room.update()
        assertEquals(0, room.dealerHand.getCards().size, "после окна — board сброшен")

        val allTypes = typesSent()
        assertTrue(
            allTypes.contains(GAME_ROOM_STATUS),
            "GAME_ROOM_STATUS должен прилететь ПОСЛЕ окна reveal, не до",
        )
    }

    @Test
    fun `busted player becomes observer (seated, unfunded) instead of being kicked, HU game pauses`() {
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        room.onRoomStarted()

        val buyIn = pokerProps.buyIn.toDouble()
        room.onBuyIn(sb, BetEvent(buyIn))
        room.onBuyIn(bb, BetEvent(buyIn))

        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()
        room.onPlayerDecision(bb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()

        // Закрываем reveal-окно → resetTable.
        advance(pokerProps.showdownRevealMs + 1)
        room.update()

        // Колода тасуется случайно — на редкой ничье оба сохраняют стек, bust-а
        // нет. Тогда нечего проверять про observer — пропускаем дил, а не падаем.
        val observers = room.map.getPlayers().filter { !it.boughtIn }
        assumeTrue(observers.size == 1, "tie — no bust this deal, observer behaviour not exercised")

        // Раньше busted-игрок мгновенно кикался (GAME_ROOM_CLOSE + removePlayer).
        // Теперь он остаётся за столом как observer: оба места заняты, проигравший
        // boughtIn=false и stack=0, GAME_ROOM_CLOSE на этом шаге НЕ шлётся —
        // у него есть OBSERVER_GRACE_MS на re-buy.
        assertEquals(2, room.map.getPlayers().size, "busted-игрок не кикается — переходит в observer")
        val observer = observers.single()
        assertEquals(0.0, observer.stack, "у observer-а пустой стек")
        assertFalse(room.isGameStarted(), "HU без второго профинансированного игрока — игра на паузе")

        val captor = argumentCaptor<Any>()
        verify(webSocketSessionService, atLeastOnce()).send(any<PlayerSession>(), captor.capture())
        val closeCount = captor.allValues.mapNotNull { (it as? Message)?.type }.count { it == GAME_ROOM_CLOSE }
        assertEquals(0, closeCount, "busted-игроку GAME_ROOM_CLOSE сразу НЕ шлётся — он observer, а не кикнут")
    }

    @Test
    fun `HU postflop both ALL_IN with unequal stacks (full-raise) deals to river without freezing`() {
        // Регрессия. Сценарий из user-report (2026-05-23): "та же ошибка, только
        // теперь зависло на TURN". Корень: на постфлопе первый ALL_IN дёргает
        // markRaiser → roundFirstActor сдвигается на него. Второй ALL_IN с бо́льшим
        // стеком — full-raise (newBet >= prevMax+bigBlind) — снова двигает
        // roundFirstActor. currentLastPlayer теперь указывает на уже-allin-нутого
        // оппонента, currentPosition != currentLastPlayer, и nextMove идёт в
        // advanceToNextActivePosition. Там не находит non-folded/non-allin
        // игрока и молча выходит → стол замерзает на флопе/тёрне.
        // Фикс: nextMove проверяет !hasActiveActor() и сразу идёт в onDealerTurn,
        // который дораздаёт улицы и через рекурсию уходит в showdown.
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        room.onRoomStarted()

        // Неравные стеки: bb имеет больше → его post-flop ALL_IN будет full-raise
        // над sb-шным. minBuyIn проверяется на onBuyIn, ставим оба ≥ minBuyIn,
        // но bb-стек кратно больше.
        val smallBuyIn = pokerProps.buyIn.toDouble()
        val bigBuyIn = smallBuyIn * 3
        room.onBuyIn(sb, BetEvent(smallBuyIn))
        room.onBuyIn(bb, BetEvent(bigBuyIn))

        // Преfлоп: оба чекают/коллируют до флопа без ALL_IN.
        // SB (P0) acts first preflop в HU → он коллирует BB.
        // SB должен докинуть до BB: currentBet(SB)=smallBlind, lastMaxBet=bigBlind,
        // → CALL amount = bigBlind - smallBlind = bet/2.
        room.onPlayerDecision(
            sb,
            PokerPlayerDecisionEvent(PokerDecision.CALL.name, room.bet / 2),
        )
        room.update()
        // BB чекает, флоп раздаётся.
        room.onPlayerDecision(bb, PokerPlayerDecisionEvent(PokerDecision.CHECK.name, null))
        room.update()
        assertEquals(3, room.dealerHand.getCards().size, "после preflop-end должны выйти 3 карты flop")

        // Флоп. SB acts first (per resetCurrentPositionForNewRound в этой реализации).
        // SB ALL_IN — короткий стек.
        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()
        assertEquals(1, room.actorPosition(), "после SB ALL_IN на флопе actor должен встать на BB")

        // BB ALL_IN — full-raise (стек кратно больше). До фикса nextMove тут
        // не находил active actor-а и оставлял стол замороженным на size=3.
        room.onPlayerDecision(bb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()

        assertEquals(
            5,
            room.dealerHand.getCards().size,
            "после double ALL_IN на флопе должны быть раздан turn+river → board=5",
        )
        assertNull(room.actorPosition(), "actor=null в SHOWDOWN")

        val bcastCaptor = argumentCaptor<Any>()
        verify(webSocketSessionService, atLeastOnce())
            .sendBroadcast(any<Collection<PlayerSession>>(), bcastCaptor.capture())
        val showdownBroadcasts =
            bcastCaptor.allValues
                .mapNotNull { it as? Message }
                .count { it.type == SHOWDOWN_RESULT }
        assertEquals(1, showdownBroadcasts, "ровно один SHOWDOWN_RESULT broadcast")
    }

    @Test
    fun `HU turn both ALL_IN with unequal stacks deals to river without freezing`() {
        // Точное соответствие user-report: зависло на TURN (board=4).
        // Тот же root cause, что и flop-case выше; разница только в том, на
        // какой улице оба all-in-ятся. Тест держим, чтобы регрессия не уехала
        // под изменения post-flop seating order или иных правил круга.
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        room.onRoomStarted()

        val smallBuyIn = pokerProps.buyIn.toDouble()
        val bigBuyIn = smallBuyIn * 3
        room.onBuyIn(sb, BetEvent(smallBuyIn))
        room.onBuyIn(bb, BetEvent(bigBuyIn))

        // Preflop: call + check → flop.
        // SB должен докинуть до BB: currentBet(SB)=smallBlind, lastMaxBet=bigBlind,
        // → CALL amount = bigBlind - smallBlind = bet/2.
        room.onPlayerDecision(
            sb,
            PokerPlayerDecisionEvent(PokerDecision.CALL.name, room.bet / 2),
        )
        room.update()
        room.onPlayerDecision(bb, PokerPlayerDecisionEvent(PokerDecision.CHECK.name, null))
        room.update()
        assertEquals(3, room.dealerHand.getCards().size)

        // Flop: оба чек → turn.
        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.CHECK.name, null))
        room.update()
        room.onPlayerDecision(bb, PokerPlayerDecisionEvent(PokerDecision.CHECK.name, null))
        room.update()
        assertEquals(4, room.dealerHand.getCards().size, "после flop-end должна выйти 4-я карта (turn)")

        // Turn: SB ALL_IN (короткий), BB ALL_IN (full-raise).
        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()
        assertEquals(1, room.actorPosition())
        room.onPlayerDecision(bb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()

        assertEquals(
            5,
            room.dealerHand.getCards().size,
            "после double ALL_IN на тёрне должен быть раздан river → board=5",
        )
        assertNull(room.actorPosition())
    }
}
