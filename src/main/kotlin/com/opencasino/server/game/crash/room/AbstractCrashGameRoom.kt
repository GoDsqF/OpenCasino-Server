package com.opencasino.server.game.crash.room

import com.opencasino.server.config.CRASH_ROUND_RESULT
import com.opencasino.server.config.CrashRoomProperties
import com.opencasino.server.config.GAME_ROOM_CLOSE
import com.opencasino.server.config.GAME_ROOM_JOIN_SUCCESS
import com.opencasino.server.config.MESSAGE
import com.opencasino.server.config.UPDATE
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.crash.CrashCashoutEvent
import com.opencasino.server.game.crash.model.CrashEngine
import com.opencasino.server.game.crash.model.CrashPlayer
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.network.pack.crash.CrashGameUpdatePack
import com.opencasino.server.network.pack.crash.CrashPlayerView
import com.opencasino.server.network.pack.crash.CrashResultEntry
import com.opencasino.server.network.pack.crash.CrashRoundResultPack
import com.opencasino.server.network.pack.crash.CrashSettingsPack
import com.opencasino.server.network.pack.shared.GameMessagePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.rng.CrashOutcomeProvider
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.rng.RngProfile
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.CrashPhase
import com.opencasino.server.service.shared.FailureCode
import com.opencasino.server.service.shared.MessageType
import com.opencasino.server.user.BalanceLedgerReason
import com.opencasino.server.user.BalanceLedgerService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import reactor.core.Disposable
import reactor.core.scheduler.Scheduler
import java.util.ArrayDeque
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function

/**
 * Базовая комната Crash: FSM раунда, тик-луп, интеграция RNG (commit→reveal),
 * ledger-расчёт и broadcast (CRASH.md §3). Подклассы (single/multi, R3/R4)
 * переопределяют только триггер фазы BETTING ([allowOpenOnBet]/[onWaitingTick]) и
 * количество мест ([maxPlayers]).
 *
 * Сервер — единственный источник истины о множителе (§0): crashPoint фиксируется в
 * commit до приёма ставок и держится в памяти до reveal. Множитель — чистая функция
 * серверного времени ([CrashEngine]); клиентское число никогда не доверяется.
 *
 * Совмещает boilerplate GameRoom/Publisher и игровую FSM (в poker это разнесено на
 * Abstract*GameRoom + *GameRoom) — отсюда `TooManyFunctions`-suppress.
 */
@Suppress("TooManyFunctions")
abstract class AbstractCrashGameRoom protected constructor(
    private var gameRoomId: UUID,
    private val schedulerService: Scheduler,
    protected val roomService: RoomService,
    protected val webSocketSessionService: WebSocketSessionService,
    protected val randomnessService: RandomnessService,
    protected val roomProperties: CrashRoomProperties,
    rngProfile: RngProfile,
    private val ledgerService: BalanceLedgerService? = null,
) : GameRoom {
    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
        private const val GAME_TYPE = "CRASH"
        private const val OUTCOME_WIN = "WIN"
        private const val OUTCOME_LOSE = "LOSE"
        private const val HUNDREDTHS = 100.0
    }

    abstract val maxPlayers: Int
    protected abstract val mode: String

    private val engine = CrashEngine(roomProperties.growthRate)
    private val provider = CrashOutcomeProvider(rngProfile)
    private val maxPayout: Double = rngProfile.maxPayout

    private val roomFutureList: MutableList<Disposable> = ArrayList()
    private val sessions: MutableMap<String, PlayerSession> = LinkedHashMap()

    @Volatile
    protected var phase: CrashPhase = CrashPhase.WAITING
        private set

    // Round-level state. Доступ — под монитором this (тот же, что update/onBet/onCashout).
    private var roundId: UUID? = null
    private var crashPoint: Double = 1.0

    // Multi выводит crashPoint на закрытии окна BETTING (из ставок), поэтому в начале
    // RUNNING он может быть ещё не готов. Гейтит обвал/расчёт, пока derive не резолвнётся
    // (окно — миллисекунды). Single ставит его сразу в commit.
    @Volatile
    private var crashPointReady: Boolean = false
    private var serverSeedHash: String = ""
    private var clientSeed: String = ""
    private var roundStartEpochMs: Long = 0
    private var bettingDeadlineEpochMs: Long = 0
    private var cooldownDeadlineEpochMs: Long = 0
    private val tickSeq = AtomicLong(0)
    private val crashHistory = ArrayDeque<Double>()

    // Кольцо последних кадров UPDATE для honest cash-out tick-anchoring (§5.3).
    private data class TickFrame(
        val seq: Long,
        val sentEpochMs: Long,
        val multiplier: Double,
    )

    private val recentTicks = ArrayDeque<TickFrame>()

    // --- lifecycle ---------------------------------------------------------

    override fun onRoomCreated(userSessions: List<PlayerSession>) {
        userSessions.forEach { session ->
            sessions[session.id] = session
            sendBroadcast(MessageType.ROOM, "${(session.player as? CrashPlayer)?.id} joined")
        }
        sendBroadcast(Message(GAME_ROOM_JOIN_SUCCESS, settingsPack()))
        schedulePeriodically(this, roomProperties.initDelay, roomProperties.loopRate)
        log.trace("Crash room {} created", key())
    }

    // Подсадка игрока в уже живую multi-комнату (тик-луп запущен первым игроком).
    // Single сюда не ходит — там комната-на-игрока. Следующий UPDATE-тик ресинкает
    // новичка на текущую фазу/кривую (CRASH.md §7), settings шлём сразу.
    fun addLatePlayer(userSession: PlayerSession) {
        synchronized(this) { sessions[userSession.id] = userSession }
        send(userSession, Message(GAME_ROOM_JOIN_SUCCESS, settingsPack()))
    }

    protected fun settingsPack(): CrashSettingsPack =
        CrashSettingsPack(
            roomId = gameRoomId.toString(),
            mode = mode,
            loopRate = roomProperties.loopRate,
            minBet = roomProperties.minBet,
            maxBet = roomProperties.maxBet,
            bettingWindowMs = roomProperties.bettingWindowMs,
            cooldownMs = roomProperties.cooldownMs,
            growthRate = roomProperties.growthRate,
            maxPayout = maxPayout,
        )

    // Crash раунды управляются FSM-тиком (см. doUpdate), отдельных room/game-start
    // сигналов нет — lifecycle сводится к onRoomCreated + тик-лупу.
    override fun onRoomStarted() = Unit

    override fun onGameStarted() = Unit

    // --- bet / cash-out ----------------------------------------------------

    fun onBet(
        userSession: PlayerSession,
        event: BetEvent,
    ) {
        val player = userSession.player as? CrashPlayer ?: return
        val stake = event.bet
        synchronized(this) {
            if (player.boughtIn) return
            if (!validateStake(userSession, player, stake)) return
            if (!ensureBettingOpen(userSession)) return
            player.stake = stake
            player.balance -= stake
            player.boughtIn = true
            player.autoCashoutTarget = event.autoCashoutTarget?.takeIf { it > 1.0 }
        }
        debitLedger(player, stake)
    }

    private fun validateStake(
        userSession: PlayerSession,
        player: CrashPlayer,
        stake: Double,
    ): Boolean {
        val min = roomProperties.minBet
        val max = roomProperties.maxBet
        val failure =
            when {
                stake <= 0.0 -> FailureCode.INVALID_BET to "Stake must be positive"
                stake < min -> FailureCode.BET_BELOW_MIN to "Stake below minimum $min"
                stake > max -> FailureCode.INVALID_BET to "Stake above maximum $max"
                stake > player.balance -> FailureCode.INSUFFICIENT_FUNDS to "Insufficient balance"
                else -> return true
            }
        sendBetFailure(userSession, failure.first, failure.second)
        return false
    }

    // Гейт фазы: WAITING — single открывает окно по ставке (multi возвращает false,
    // его окно cadence-driven); BETTING — окно уже открыто; иначе раунд идёт.
    private fun ensureBettingOpen(userSession: PlayerSession): Boolean {
        when (phase) {
            CrashPhase.WAITING ->
                if (allowOpenOnBet()) {
                    openBetting(currentTimeMillis())
                } else {
                    sendBetFailure(userSession, FailureCode.ROOM_CLOSED, "Betting is not open")
                    return false
                }
            CrashPhase.BETTING -> Unit
            else -> {
                sendBetFailure(userSession, FailureCode.GAME_ALREADY_STARTED, "Round in progress")
                return false
            }
        }
        return true
    }

    fun onCashout(
        userSession: PlayerSession,
        event: CrashCashoutEvent,
    ) {
        synchronized(this) {
            if (phase != CrashPhase.RUNNING || !crashPointReady) return
            val player = userSession.player as? CrashPlayer ?: return
            if (!player.boughtIn || player.cashedOutAt != null) return
            val now = currentTimeMillis()
            settleCashout(player, multiplierForTick(event.lastTickSeq, now))
        }
    }

    // Множитель кадра, который сервер сам выпустил (§5.3): если игрок назвал кадр в
    // пределах cashoutLagCompCapMs — берём его множитель, иначе fallback на
    // receive-time. Назвать будущий кадр нельзя (его ещё не отправляли); назвать
    // более ранний — хуже игроку. Под монитором this.
    private fun multiplierForTick(
        lastTickSeq: Long,
        now: Long,
    ): Double {
        val frame = recentTicks.firstOrNull { it.seq == lastTickSeq }
        if (frame != null && now - frame.sentEpochMs <= roomProperties.cashoutLagCompCapMs) {
            return frame.multiplier
        }
        return engine.multiplierAt(now - roundStartEpochMs)
    }

    private fun settleCashout(
        player: CrashPlayer,
        multiplier: Double,
    ) {
        val effective = minOf(multiplier, crashPoint)
        player.cashedOutAt = effective
        val payout = round2(player.stake * effective)
        player.payout = payout
        player.balance += payout
        creditLedger(player, payout)
    }

    // --- FSM tick ----------------------------------------------------------

    // Тик-луп не должен умирать от исключения в одном раунде — ловим широко и
    // логируем, иначе schedulePeriodically больше не вызовет update (мёртвый стол).
    @Suppress("TooGenericExceptionCaught")
    override fun run() {
        try {
            update()
        } catch (e: Exception) {
            log.error("crash room update exception", e)
        }
    }

    override fun update() = synchronized(this) { doUpdate() }

    private fun doUpdate() {
        val now = currentTimeMillis()
        when (phase) {
            CrashPhase.WAITING -> onWaitingTick(now)
            CrashPhase.BETTING ->
                if (now >= bettingDeadlineEpochMs && roundId != null) {
                    startRunning(now)
                    broadcastTick(now, engine.multiplierAt(0))
                } else {
                    broadcastTick(now, 1.0)
                }
            CrashPhase.RUNNING -> runningTick(now)
            CrashPhase.CRASHED -> Unit // переход в COOLDOWN делается синхронно в crash()
            CrashPhase.COOLDOWN ->
                if (now >= cooldownDeadlineEpochMs) {
                    phase = CrashPhase.WAITING
                    onCooldownComplete(now)
                } else {
                    broadcastTick(now, crashPoint)
                }
        }
    }

    private fun runningTick(now: Long) {
        val multiplier = engine.multiplierAt(now - roundStartEpochMs)
        // Исход ещё выводится (multi: derive после закрытия окна BETTING) — кривая
        // идёт для resync, но обвал/расчёт недоступны до готового crashPoint. Окно —
        // миллисекунды (HMAC синхронен, ждём только async-insert аудита).
        if (!crashPointReady) {
            broadcastTick(now, multiplier)
            return
        }
        // Авто-cash-out: выводим при m(t) >= target, но только если target <= crashPoint
        // (иначе обвал раньше — игрок проигрывает). Расчёт по серверным часам, без
        // сетевой гонки (§5.2). Settle ровно на target, а не на overshoot тика.
        players().forEach { player ->
            val target = player.autoCashoutTarget
            if (target != null && shouldAutoCashout(player, target, multiplier)) {
                settleCashout(player, target)
            }
        }
        if (multiplier >= crashPoint) {
            crash(now)
            return
        }
        broadcastTick(now, multiplier)
    }

    // target <= crashPoint — иначе обвал раньше, и авто-вывод не срабатывает
    // (игрок проигрывает). Цель уже достигнута (multiplier >= target) и ещё не выведен.
    private fun shouldAutoCashout(
        player: CrashPlayer,
        target: Double,
        multiplier: Double,
    ): Boolean = player.boughtIn && player.cashedOutAt == null && target <= crashPoint && multiplier >= target

    // Открытие окна BETTING. Single (commit-at-open): clientSeed = серверная соль,
    // crashPoint выводится сразу. Multi (commit-at-run-start, см.
    // [outcomeDerivedAtRunStart]): только резервируем seed и публикуем хэш — clientSeed
    // зависит от ставок раунда и считается в [startRunning] (CRASH.md §11).
    protected fun openBetting(now: Long) {
        resetRound()
        phase = CrashPhase.BETTING
        bettingDeadlineEpochMs = now + roomProperties.bettingWindowMs
        if (outcomeDerivedAtRunStart()) {
            randomnessService
                .reserve()
                .subscribe { reservation ->
                    synchronized(this) {
                        roundId = reservation.roundId
                        serverSeedHash = reservation.serverSeedHash
                    }
                }
        } else {
            val seed = buildClientSeed()
            clientSeed = seed
            randomnessService
                .commit(GAME_TYPE, seed, provider)
                .subscribe { commit ->
                    synchronized(this) {
                        roundId = commit.roundId
                        crashPoint = commit.outcome
                        serverSeedHash = commit.serverSeedHash
                        crashPointReady = true
                    }
                }
        }
    }

    private fun startRunning(now: Long) {
        phase = CrashPhase.RUNNING
        roundStartEpochMs = now
        tickSeq.set(0)
        recentTicks.clear()
        if (!outcomeDerivedAtRunStart()) return
        val seed = buildClientSeed()
        clientSeed = seed
        val rid = roundId ?: return
        randomnessService
            .derive(rid, GAME_TYPE, seed, provider)
            .subscribe { commit ->
                synchronized(this) {
                    crashPoint = commit.outcome
                    serverSeedHash = commit.serverSeedHash
                    crashPointReady = true
                }
            }
    }

    private fun crash(now: Long) {
        phase = CrashPhase.CRASHED
        val finalCrashPoint = crashPoint
        crashHistory.addFirst(finalCrashPoint)
        while (crashHistory.size > roomProperties.historySize) crashHistory.removeLast()

        val entries =
            players()
                .filter { it.boughtIn }
                .map { player ->
                    val won = player.cashedOutAt != null
                    CrashResultEntry(
                        id = player.id,
                        displayName = player.displayName,
                        outcome = if (won) OUTCOME_WIN else OUTCOME_LOSE,
                        stake = player.stake,
                        cashedOutAt = player.cashedOutAt,
                        payout = player.payout,
                    )
                }
        val rid = roundId
        val snapshotHash = serverSeedHash
        val snapshotClientSeed = clientSeed
        if (rid != null) {
            randomnessService
                .reveal(rid)
                .defaultIfEmpty("")
                .subscribe { revealed ->
                    sendBroadcast(
                        Message(
                            CRASH_ROUND_RESULT,
                            CrashRoundResultPack(
                                roundId = rid.toString(),
                                crashPoint = finalCrashPoint,
                                serverSeedHash = snapshotHash,
                                revealedServerSeed = revealed,
                                clientSeed = snapshotClientSeed,
                                players = entries,
                            ),
                        ),
                    )
                }
        }
        phase = CrashPhase.COOLDOWN
        cooldownDeadlineEpochMs = now + roomProperties.cooldownMs
        broadcastTick(now, finalCrashPoint)
    }

    private fun resetRound() {
        roundId = null
        crashPoint = 1.0
        crashPointReady = false
        serverSeedHash = ""
        clientSeed = ""
        roundStartEpochMs = 0
        tickSeq.set(0)
        recentTicks.clear()
        players().forEach { player ->
            player.stake = 0.0
            player.boughtIn = false
            player.autoCashoutTarget = null
            player.cashedOutAt = null
            player.payout = 0.0
        }
    }

    // --- extension points (single/multi, R3/R4) ----------------------------

    // WAITING: single открывает BETTING по приходу ставки (см. onBet); multi
    // переопределяет, чтобы открывать окно по каденции.
    protected open fun allowOpenOnBet(): Boolean = true

    protected open fun onWaitingTick(now: Long) {}

    // COOLDOWN истёк: single переходит в WAITING (следующая ставка откроет раунд);
    // multi переопределяет, чтобы сразу открыть новое окно BETTING.
    protected open fun onCooldownComplete(now: Long) {}

    // Когда выводится исход раунда. false (single) — в commit на открытии окна BETTING,
    // clientSeed = серверная соль. true (multi) — в derive на закрытии окна, clientSeed
    // зависит от ставок (CRASH.md §11): сервер уже опубликовал serverSeedHash и не может
    // подобрать seed под известные ставки.
    protected open fun outcomeDerivedAtRunStart(): Boolean = false

    // clientSeed раунда. Single — серверная соль (UUID): исход всё равно фиксирован
    // позицией в хэш-цепочке. Multi переопределяет хэшем ставок раунда; вызывается
    // на закрытии окна BETTING, когда ставки собраны.
    protected open fun buildClientSeed(): String = UUID.randomUUID().toString()

    // Обёртка для тестов: VirtualTimeScheduler не двигает System-часы (см. poker
    // PokerHUDoubleAllInTest). Тесты переопределяют, чтобы детерминированно
    // прогонять FSM.
    internal open fun currentTimeMillis(): Long = System.currentTimeMillis()

    // --- broadcast ---------------------------------------------------------

    private fun broadcastTick(
        now: Long,
        multiplier: Double,
    ) {
        val seq = tickSeq.incrementAndGet()
        if (phase == CrashPhase.RUNNING) {
            recentTicks.addLast(TickFrame(seq, now, multiplier))
            val cap = roomProperties.cashoutLagCompCapMs
            while (recentTicks.isNotEmpty() && now - recentTicks.first().sentEpochMs > cap) {
                recentTicks.removeFirst()
            }
        }
        val history = crashHistory.toList()
        sessions.values.forEach { session ->
            send(session, Message(UPDATE, buildUpdatePack(session, multiplier, seq, now, history)))
        }
    }

    private fun buildUpdatePack(
        recipient: PlayerSession,
        multiplier: Double,
        seq: Long,
        now: Long,
        history: List<Double>,
    ): CrashGameUpdatePack {
        val recipientId = (recipient.player as? CrashPlayer)?.id
        return CrashGameUpdatePack(
            phase = phase,
            roundStartEpochMs = if (phase == CrashPhase.RUNNING) roundStartEpochMs else null,
            bettingDeadlineEpochMs = if (phase == CrashPhase.BETTING) bettingDeadlineEpochMs else null,
            cooldownDeadlineEpochMs = if (phase == CrashPhase.COOLDOWN) cooldownDeadlineEpochMs else null,
            tickSeq = seq,
            tickSentEpochMs = now,
            serverMultiplier = multiplier,
            crashHistory = history,
            serverSeedHash = serverSeedHash,
            players =
                players().map { player ->
                    val isYou = player.id == recipientId
                    CrashPlayerView(
                        id = player.id,
                        displayName = player.displayName,
                        stake = player.stake,
                        cashedOutAt = player.cashedOutAt,
                        autoCashoutTarget = if (isYou) player.autoCashoutTarget else null,
                        isYou = isYou,
                    )
                },
        )
    }

    // --- ledger ------------------------------------------------------------

    private fun debitLedger(
        player: CrashPlayer,
        stake: Double,
    ) {
        val uid = player.userSession.userId ?: return
        ledgerService
            ?.applyDelta(uid, roundId ?: UUID.randomUUID(), -stake, BalanceLedgerReason.CRASH_BET)
            ?.subscribe()
    }

    private fun creditLedger(
        player: CrashPlayer,
        payout: Double,
    ) {
        if (payout <= 0.0) return
        val uid = player.userSession.userId ?: return
        ledgerService
            ?.applyDelta(uid, roundId ?: UUID.randomUUID(), payout, BalanceLedgerReason.CRASH_PAYOUT)
            ?.subscribe()
    }

    private fun round2(value: Double): Double = Math.round(value * HUNDREDTHS) / HUNDREDTHS

    protected fun players(): List<CrashPlayer> = sessions.values.mapNotNull { it.player as? CrashPlayer }

    // --- GameRoom boilerplate ----------------------------------------------

    override fun onDestroy(userSessions: List<PlayerSession>) {
        userSessions.forEach { sessions.remove(it.id) }
    }

    override fun onDisconnect(userSession: PlayerSession): PlayerSession {
        val removed = sessions.remove(userSession.id) ?: return userSession
        if (sessions.isEmpty()) roomService.onGameEnd(this)
        return removed
    }

    override fun onReattach(
        oldSession: PlayerSession,
        newSession: PlayerSession,
    ) {
        if (sessions.remove(oldSession.id) == null) return
        sessions[newSession.id] = newSession
        (newSession.player as? CrashPlayer)?.let {
            it.userSession = newSession
            it.disconnected = false
        }
    }

    override fun onGraceStart(userSession: PlayerSession) {
        (userSession.player as? CrashPlayer)?.disconnected = true
    }

    override fun onPlayerInfoRequest(userSession: PlayerSession) {
        send(userSession, Message(GAME_ROOM_JOIN_SUCCESS, settingsPack()))
    }

    override fun onClose(userSession: PlayerSession) {
        send(userSession, Message(GAME_ROOM_CLOSE))
    }

    override fun close(): Collection<PlayerSession> {
        val result = sessions.values.toList()
        result.forEach { onClose(it) }
        roomFutureList.forEach { it.dispose() }
        log.trace("Crash room {} closed", key())
        return result
    }

    override fun schedule(
        runnable: Runnable,
        delayMillis: Long,
    ) = roomFutureList.add(schedulerService.schedule(runnable, delayMillis, TimeUnit.MILLISECONDS))

    override fun schedulePeriodically(
        runnable: Runnable,
        initDelay: Long,
        loopRate: Long,
    ) = roomFutureList.add(schedulerService.schedulePeriodically(runnable, initDelay, loopRate, TimeUnit.MILLISECONDS))

    override fun send(
        userSession: PlayerSession,
        message: Any,
    ) = webSocketSessionService.send(userSession, message)

    override fun send(
        userSession: PlayerSession,
        function: Function<PlayerSession, Any>,
    ) = webSocketSessionService.send(userSession, function)

    override fun sendBroadcast(message: Any) = webSocketSessionService.sendBroadcast(sessions.values, message)

    override fun sendBroadcast(messageFunction: Function<PlayerSession, Any>) =
        webSocketSessionService.sendBroadcast(sessions.values, messageFunction)

    override fun sendBroadcast(
        userSessions: Collection<PlayerSession>,
        message: Any,
    ) = webSocketSessionService.sendBroadcast(userSessions, message)

    override fun sendBroadcast(
        userSessions: Collection<PlayerSession>,
        function: Function<PlayerSession, Any>,
    ) = webSocketSessionService.sendBroadcast(userSessions, function)

    override fun sendBroadcast(
        type: MessageType,
        message: String,
    ) = sendBroadcast(Message(MESSAGE, GameMessagePack(type.type, message)))

    override fun sendFailure(
        userSession: PlayerSession,
        code: FailureCode,
        message: String,
        details: Any?,
    ) = webSocketSessionService.sendFailure(userSession, code, message, details)

    override fun sendBetFailure(
        userSession: PlayerSession,
        code: FailureCode,
        message: String,
        details: Any?,
    ) = webSocketSessionService.sendBetFailure(userSession, code, message, details)

    override fun sendJoinFailure(
        userSession: PlayerSession,
        code: FailureCode,
        message: String,
        details: Any?,
    ) = webSocketSessionService.sendJoinFailure(userSession, code, message, details)

    override fun getPlayerSessionBySessionId(userSession: PlayerSession): Optional<PlayerSession> =
        Optional.ofNullable(sessions[userSession.id])

    override fun sessions(): Collection<PlayerSession> = sessions.values

    override fun currentPlayersCount(): Int = sessions.size

    override fun key(): UUID = gameRoomId

    override fun key(key: UUID) {
        gameRoomId = key
    }
}
