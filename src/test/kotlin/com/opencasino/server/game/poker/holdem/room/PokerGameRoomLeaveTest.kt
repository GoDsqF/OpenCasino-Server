package com.opencasino.server.game.poker.holdem.room

import com.opencasino.server.config.ApplicationProperties
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.socket.HandshakeInfo
import reactor.test.scheduler.VirtualTimeScheduler
import java.security.Principal
import java.time.Duration
import java.util.UUID

// Фаза A §2.6: zombie-leave bugfix + observer-mode (busted → observer, не кик).
class PokerGameRoomLeaveTest {
    private val handshake: HandshakeInfo = mock()
    private val webSocketSessionService: WebSocketSessionService = mock()
    private val ledgerService: BalanceLedgerService = mock()
    private val roomService: RoomService = mock()
    private val appProps = ApplicationProperties()
    private val pokerProps = appProps.pokerRoom

    // Реальный планировщик комнаты — тест двигает его время вручную, чтобы
    // выстрелил observer-grace таймер (см. startObserverGrace).
    private val scheduler: VirtualTimeScheduler = VirtualTimeScheduler.create()

    // Подменяемое "время" для reveal-окна (currentTimeMillis), независимо от scheduler.
    private var fakeNow: Long = 0L

    private fun newRoom(): PokerGameRoom =
        object : PokerGameRoom(
            PokerMap(),
            UUID.randomUUID(),
            roomService,
            webSocketSessionService,
            scheduler,
            appProps.game,
            pokerProps,
            ledgerService,
        ) {
            override fun currentTimeMillis(): Long = fakeNow
        }

    private fun advanceClock(ms: Long) {
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

    // HU: оба buy-in, оба ALL_IN префлоп → раздаётся весь board → showdown.
    // closeReveal=true дополнительно закрывает reveal-окно → запускает resetTable
    // (где busted-игрок переходит в observer). false — останавливаемся в reveal.
    private fun driveHuToShowdown(
        room: PokerGameRoom,
        sb: PlayerSession,
        bb: PlayerSession,
        closeReveal: Boolean,
    ) {
        room.onRoomStarted()
        val buyIn = pokerProps.buyIn.toDouble()
        room.onBuyIn(sb, BetEvent(buyIn))
        room.onBuyIn(bb, BetEvent(buyIn))
        room.onPlayerDecision(sb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()
        room.onPlayerDecision(bb, PokerPlayerDecisionEvent(PokerDecision.ALL_IN.name, null))
        room.update()
        if (closeReveal) {
            advanceClock(pokerProps.showdownRevealMs + 1)
            room.update()
        }
    }

    @Test
    fun `LEAVE during showdown reveal removes the seat immediately (zombie-leave fix)`() {
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        driveHuToShowdown(room, sb, bb, closeReveal = false)
        assertNull(room.actorPosition(), "reveal-окно: roundEnd=true, actor=null")

        // До фикса onDisconnect снимал только WS-сессию, PokerPlayer оставался в
        // map → «зомби-сидящий» в players[] до следующего resetTable (а в HU его
        // и не было — игра вставала). Теперь LEAVE сразу освобождает место.
        room.onPlayerLeave(sb)

        assertNull(room.map.getPlayerById(1L), "LEAVE должен сразу убрать игрока из стола")
        assertEquals(1, room.map.getPlayers().size, "за столом остаётся только второй игрок")
    }

    @Test
    fun `onGraceStart does not fold the acting player but grace-expiry onDisconnect does`() {
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        room.onRoomStarted()
        val buyIn = pokerProps.buyIn.toDouble()
        room.onBuyIn(sb, BetEvent(buyIn))
        room.onBuyIn(bb, BetEvent(buyIn))

        val actorPos = room.actorPosition()
        assertNotNull(actorPos, "префлоп: есть актор")
        val actor = room.map.getPlayers().single { it.position == actorPos }
        assertFalse(actor.folded)

        // Refresh на своём ходу: старт грейса помечает дисконнект, но НЕ фолдит —
        // игрок успеет ребаунднуться в окне disconnectGraceMs.
        room.onGraceStart(actor.userSession)
        assertTrue(actor.disconnected, "грейс помечает дисконнект")
        assertFalse(actor.folded, "refresh на своём ходу больше не фолдит сразу")

        // Не вернулся за окно грейса → сервис заходит в onDisconnect, который и
        // делает auto-fold + двигает игру дальше.
        room.onDisconnect(actor.userSession)
        assertTrue(actor.folded, "по истечении грейса игрок фолдится в onDisconnect")
    }

    @Test
    fun `seated room is not torn down by a fixed lifetime timer`() {
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        // onRoomCreated раньше планировал onGameEnd через endDelay+startDelay.
        room.onRoomCreated(emptyList())

        scheduler.advanceTimeBy(Duration.ofMillis(pokerProps.endDelay + pokerProps.startDelay + 5_000))

        verify(roomService, never()).onGameEnd(room)
        assertEquals(2, room.map.getPlayers().size, "игроки на месте — комната жива")
    }

    @Test
    fun `busted player transitions to observer with observer and needsRebuy flags set`() {
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        driveHuToShowdown(room, sb, bb, closeReveal = true)

        val observers = room.map.getPlayers().filter { !it.boughtIn }
        assumeTrue(observers.size == 1, "tie — no bust this deal, observer transition not exercised")
        val observer = observers.single()
        val funded = room.map.getPlayers().single { it.boughtIn }

        assertEquals(0.0, observer.stack, "у observer-а пустой стек")
        assertTrue(observer.getPublicUpdatePack(isYou = false).observer, "public observer-флаг выставлен")
        assertTrue(observer.getPrivateUpdatePack().needsRebuy, "private needsRebuy выставлен")
        assertFalse(funded.getPublicUpdatePack(isYou = false).observer, "профинансированный — не observer")
        assertFalse(funded.getPrivateUpdatePack().needsRebuy, "профинансированному rebuy не нужен")
    }

    @Test
    fun `observer re-buy cancels auto-leave and resumes the paused HU game`() {
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        driveHuToShowdown(room, sb, bb, closeReveal = true)

        val observers = room.map.getPlayers().filter { !it.boughtIn }
        assumeTrue(observers.size == 1, "tie — no bust this deal")
        val observer = observers.single()
        assertFalse(room.isGameStarted(), "HU на паузе, пока observer не профинансирован")

        room.onBuyIn(observer.userSession, BetEvent(pokerProps.buyIn.toDouble()))

        assertTrue(observer.boughtIn, "re-buy профинансировал игрока")
        assertFalse(observer.getPublicUpdatePack(isYou = false).observer, "после re-buy уже не observer")
        assertTrue(room.isGameStarted(), "оба профинансированы — новая раздача стартует")

        // grace истёк, но таймер был отменён re-buy-ем → auto-leave не срабатывает.
        scheduler.advanceTimeBy(Duration.ofMillis(pokerProps.observerGraceMs + 1))
        verify(webSocketSessionService, never()).onPlayerLeave(observer.userSession)
    }

    @Test
    fun `observer is auto-removed after the grace window expires`() {
        val room = newRoom()
        val sb = newSession()
        val bb = newSession()
        seatFresh(room, sb, 1L)
        seatFresh(room, bb, 2L)
        // Auto-LEAVE в проде идёт через сервис, который заходит обратно в
        // room.onPlayerLeave; здесь сервис замокан — маршрутизируем вручную,
        // чтобы проверить и факт вызова, и эффект (освобождение места).
        doAnswer {
            room.onPlayerLeave(it.getArgument<PlayerSession>(0))
            null
        }.whenever(webSocketSessionService).onPlayerLeave(any())

        driveHuToShowdown(room, sb, bb, closeReveal = true)
        val observers = room.map.getPlayers().filter { !it.boughtIn }
        assumeTrue(observers.size == 1, "tie — no bust this deal")
        val observer = observers.single()

        scheduler.advanceTimeBy(Duration.ofMillis(pokerProps.observerGraceMs - 1))
        assertNotNull(room.map.getPlayerById(observer.id), "до истечения grace observer остаётся за столом")

        scheduler.advanceTimeBy(Duration.ofMillis(2))
        verify(webSocketSessionService).onPlayerLeave(observer.userSession)
        assertNull(room.map.getPlayerById(observer.id), "после grace observer высажен из стола")
    }
}
