package com.opencasino.server.game.poker.holdem.room

import com.opencasino.server.config.*
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.poker.PokerPlayerDecisionEvent
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.poker.holdem.map.PokerMap
import com.opencasino.server.game.poker.holdem.model.PokerBetType
import com.opencasino.server.game.poker.holdem.model.PokerContestant
import com.opencasino.server.game.poker.holdem.model.PokerDistribution
import com.opencasino.server.game.poker.holdem.model.PokerHand
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.game.poker.holdem.model.PokerSidePotDistribution
import com.opencasino.server.network.pack.poker.info.InfoPack
import com.opencasino.server.network.pack.poker.shared.GameSettingsPack
import com.opencasino.server.network.pack.poker.shared.RoomPack
import com.opencasino.server.network.pack.poker.showdown.PokerShowdownEntry
import com.opencasino.server.network.pack.poker.showdown.PokerShowdownPack
import com.opencasino.server.network.pack.poker.showdown.PokerShowdownSidePot
import com.opencasino.server.network.pack.poker.update.GameUpdatePack
import com.opencasino.server.network.pack.shared.DealerUpdatePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.ClientState
import com.opencasino.server.service.shared.FailureCode
import com.opencasino.server.service.shared.PokerDecision
import com.opencasino.server.service.shared.PokerPhase
import com.opencasino.server.user.BalanceLedgerReason
import com.opencasino.server.user.BalanceLedgerService
import reactor.core.Disposable
import reactor.core.scheduler.Scheduler
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

open class PokerGameRoom(
    val map: PokerMap,
    gameRoomId: UUID,
    roomService: RoomService,
    webSocketSessionService: WebSocketSessionService,
    schedulerService: Scheduler,
    val gameProperties: GameProperties,
    val roomProperties: PokerRoomProperties,
    private val ledgerService: BalanceLedgerService? = null,
) : AbstractPokerGameRoom(gameRoomId, schedulerService, roomService, webSocketSessionService) {
    // can i delete this later?
    var minLimit: Double? = null
    var maxLimit: Double? = null

    //
    var betType: PokerBetType = PokerBetType.PotLimit
    var bet: Double = 100.00

    // Per-room overrides — заполняются один раз в PokerRoomServiceImpl.applyInitialSettings()
    // при создании стола. Null = брать значение из PokerRoomProperties.
    // maxPlayers — capacity, ограниченный сверху глобальным MAX_POKER_PLAYERS,
    // снизу — MIN_POKER_PLAYERS (см. service-validation).
    var maxPlayers: Int = roomProperties.maxPlayers
    var minBuyIn: Double = roomProperties.buyIn.toDouble()
    var maxBuyIn: Double? = null

    /** Settings можно проставлять только один раз (на CREATE). После — игнорировать. */
    private val settingsLocked = AtomicBoolean(false)

    fun lockSettings(): Boolean = settingsLocked.compareAndSet(false, true)

    fun settingsAreLocked(): Boolean = settingsLocked.get()

    // explains itself
    var pot: Double = 0.00
    var lastMaxBet: Double = 0.00

    // Производные от bet (== big blind). Getter-ы, а не val-ы: bet проставляется
    // в updateSettings ПОСЛЕ конструктора, поэтому зафиксированные на construction-time
    // val-ы давали стейл-блайнды (50/100) для столов с кастомным bet.
    val smallBlind: Double get() = bet / 2
    val bigBlind: Double get() = bet

    // Позиция SB (== button в HU). Ротация на +1 происходит в resetTable()
    // в конце каждой раздачи. Внутри раздачи currentStartPlayer не меняется,
    // даже когда action переходит со street на street.
    private var currentStartPlayer: Int = 0

    // player pos expected to do something
    private var currentPosition: Int = 0

    // Позиция первого ходящего в ТЕКУЩЕМ круге торгов. Нужна для определения
    // конца круга: круг закончен, когда currentPosition стал бы (roundFirstActor - 1)
    // и все ставки уравнены. Без этого поля раньше использовался currentStartPlayer,
    // что давало неверный «last actor» на префлопе для 3+ игроков (action
    // прокручивался лишний круг — UTG ходил дважды).
    private var roundFirstActor: Int = 0

    // maybe should change for 3 bools instead
    private var dealerCardsCount: Int = 0

    // bet rules init and blinds calculation
    init {
        when (betType) {
            PokerBetType.FixedLimit -> {
                minLimit = bet / 2
                maxLimit = bet
            }
            PokerBetType.PotLimit -> {
                minLimit = bet
                maxLimit = minLimit
            }
            PokerBetType.NoLimit -> {
                minLimit = bet
            }
        }
    }

    // stores last update per player to prevent spam without cross-player aliasing
    private val lastUpdateBySession: MutableMap<String, Message> = HashMap()

    // Per-observer auto-leave таймеры (см. startObserverGrace). Ключ — playerId.
    // Отменяются на re-buy (onBuyIn) и на снятии seat (removeSeat). Доступ —
    // под монитором this (тот же, что onBuyIn/doUpdate).
    private val observerTimers: MutableMap<Long, Disposable> = HashMap()

    // game status control
    private val started = AtomicBoolean(false)
    private val gameStarted = AtomicBoolean(false)
    private val dealerTurn = AtomicBoolean(false)
    private val roundEnd = AtomicBoolean(false)

    // Время триггера showdown — для отложенного resetTable. Пока окно
    // `showdownRevealMs` не истекло, doUpdate шлёт UPDATE с post-showdown
    // state-ом, но НЕ GAME_ROOM_STATUS и НЕ запускает resetTable. Это даёт FE
    // окно проиграть staggered hole-reveal, combo-highlight и chip-fly.
    // null = нет активного showdown окна. Внутренний — null после resetTable.
    // Использовать System.currentTimeMillis(), а не nanoTime — на тиках важна
    // разница в миллисекундах, не монотонность.
    @Volatile private var showdownEndedAt: Long? = null

    // Турн-таймер актора. actorTurnStartedAt — момент, когда текущий актор получил
    // ход; turnDeadlineEpochMs = это + actionTimeoutMs. timedActorPosition —
    // позиция, под которую взведён таймер: при её смене (ход перешёл) таймер
    // перевзводится в checkTurnTimeout. Доступ — под монитором this (тот же, что
    // doUpdate/nextMove). null/-1 = таймер не взведён (showdown/пауза/нет актора).
    @Volatile private var actorTurnStartedAt: Long? = null
    private var timedActorPosition: Int = -1

    var deck = CardDeck(roomProperties.deckStacks)

    var dealerHand = CardDeck()

    private fun initialDeal() {
        val players = map.getPlayers()
        if (map.getIsHoldem()) {
            for (player in players) {
                deck.dealCards(2, player.playerDeck)
            }
        }
    }

    private fun takeBlinds() {
        // Конвенция: currentStartPlayer указывает на SB (== button в HU).
        // Раньше здесь BB шёл на currentStartPlayer, а SB — на +1: блайнды
        // были перевёрнуты, из-за чего в HU первым ходил BB вместо SB.
        val n = map.getPlayers().size
        if (n < 2) return
        map.getPlayerByPosition(currentStartPlayer % n).also {
            if (it != null) takeBlind(it, smallBlind)
        }
        map.getPlayerByPosition((currentStartPlayer + 1) % n).also {
            if (it != null) takeBlind(it, bigBlind)
        }
    }

    private fun takeBlind(
        player: PokerPlayer,
        amount: Double,
    ) {
        val taken = if (player.stack >= amount) amount else player.stack
        player.stack -= taken
        player.totalContribution += taken
        player.currentBet = (player.currentBet ?: 0.0) + taken
        if (player.currentBet!! > lastMaxBet) lastMaxBet = player.currentBet!!
        if (player.stack <= 0.0) player.allin = true
    }

    private fun initialTurn() {
        takeBlinds()
        initialDeal()
        // Preflop action: первым ходит игрок слева от BB.
        //   HU (N=2): button=SB acts first preflop → currentPosition = SB = currentStartPlayer.
        //   3+:       UTG = BB + 1 → currentPosition = (currentStartPlayer + 2) % N.
        // Раньше currentPosition не выставлялся в initialTurn и оставался от
        // прошлой раздачи (или 0 на старте) — первым ходил первый присоединившийся.
        val n = map.getPlayers().size
        if (n >= 2) {
            val preflopFirst = if (n == 2) currentStartPlayer % n else (currentStartPlayer + 2) % n
            roundFirstActor = preflopFirst
            currentPosition = firstAliveFrom(preflopFirst, n)
        }
    }

    private fun firstAliveFrom(
        start: Int,
        n: Int,
    ): Int {
        for (i in 0 until n) {
            val pos = (start + i) % n
            val candidate = map.getPlayerByPosition(pos)
            if (candidate != null && !candidate.folded && !candidate.allin) return pos
        }
        return start
    }

    fun gameSettingsPack(): GameSettingsPack =
        GameSettingsPack(
            roomId = gameRoomId.toString(),
            loopRate = roomProperties.loopRate,
            betType = betType.name,
            smallBlind = smallBlind,
            bigBlind = bigBlind,
            minBuyIn = minBuyIn,
            maxBuyIn = maxBuyIn,
        )

    fun addLatePlayer(userSession: PlayerSession) {
        val player = userSession.player as PokerPlayer
        map.addPlayer(player)
        super.onRoomCreated(listOf(userSession))
        send(
            userSession,
            Message(GAME_ROOM_JOIN_SUCCESS, gameSettingsPack()),
        )
        log.trace("Late join player {} on room {}", player.id, key())
    }

    override fun onRoomCreated(userSessions: List<PlayerSession>) {
        // assign sessions to players
        if (userSessions.isNotEmpty()) {
            userSessions.forEach {
                val player = it.player as PokerPlayer
                player.position = userSessions.size
                map.addPlayer(player)
            }
        }
        // send join messages
        super.onRoomCreated(userSessions)
        // send settings to client(optional to use, loop rate used only on server)
        sendBroadcast(
            Message(GAME_ROOM_JOIN_SUCCESS, gameSettingsPack()),
        )

        schedulePeriodically(
            this,
            roomProperties.initDelay,
            roomProperties.loopRate,
        )

        // Раньше здесь стоял безусловный snос комнаты через endDelay+startDelay
        // (≈5 мин от создания) — стол закрывался даже при активной игре. Комната
        // теперь живёт, пока в ней есть сессии: пустую закрывает onGameEnd из
        // AbstractPokerGameRoom.onDisconnect (sessions.isEmpty()).

        log.trace("Room {} has been created", key())
    }

    // start room
    override fun onRoomStarted() {
        started.set(true)
        sendBroadcast(
            Message(
                GAME_ROOM_START,
                RoomPack(
                    ZonedDateTime
                        .now(ZoneId.of("Europe/Moscow"))
                        .plus(roomProperties.startDelay, ChronoUnit.MILLIS)
                        .toInstant()
                        .toEpochMilli(),
                    gameRoomId.toString(),
                ),
            ),
        )
    }

    override fun onGameStarted() {
        log.info("Room {}. Game has been started", key())
        // GAME_START раньше нёс время конца раздачи (now + endDelay) под старый
        // фиксированный лимит жизни комнаты. Лимит убран (комната живёт, пока есть
        // игроки), время конца стало фиктивным — broadcast снят.
    }

    private fun collectUpdate(player: PokerPlayer): Message {
        if (player.isAlive) player.update()
        val updatePack = player.getPrivateUpdatePack()
        val playerUpdatePackList =
            map
                .getPlayers()
                .map { mapped ->
                    if (mapped.id != player.id) {
                        mapped.getSecretUpdatePack()
                    } else {
                        mapped.getUpdatePack()
                    }
                }

        val dealerUpdatePack = DealerUpdatePack(dealerHand.toPublicView())

        return Message(
            UPDATE,
            GameUpdatePack(
                updatePack,
                playerUpdatePackList,
                dealerUpdatePack,
                currentPhase(),
                actorPosition(),
                pot,
                lastMaxBet,
                bigBlind,
                turnDeadlineEpochMs(),
                clientStateFor(player),
            ),
        )
    }

    // Дедлайн жив только пока есть актор (actorTurnStartedAt взводится в
    // checkTurnTimeout и гасится на showdown/смене актора). Один на стол.
    private fun turnDeadlineEpochMs(): Long? = actorTurnStartedAt?.let { it + roomProperties.actionTimeoutMs }

    private fun clientStateFor(player: PokerPlayer): ClientState =
        when {
            roundEnd.get() -> ClientState.SHOWDOWN
            !player.boughtIn -> ClientState.AWAITING_BUY_IN
            !gameStarted.get() -> ClientState.AWAITING_START
            player.position == actorPosition() && !player.folded && !player.allin -> ClientState.AWAITING_TURN
            else -> ClientState.IN_ROUND
        }

    fun actorPosition(): Int? = if (roundEnd.get()) null else currentPosition

    // Перевзводит турн-таймер при смене актора и авто-ходит за просрочившего:
    // check, если коллировать нечего (бесплатный ход — фолд бессмыслен), иначе
    // fold. Зеркалит decision-путь PokerPlayer.update (folded=true → nextMove).
    // Возвращает true, если авто-действие сработало (раздача сдвинута nextMove-ом).
    private fun checkTurnTimeout(): Boolean {
        val actor = actorPosition()
        val actorPos = actor ?: -1
        if (actorPos != timedActorPosition) {
            timedActorPosition = actorPos
            actorTurnStartedAt = if (actor == null) null else currentTimeMillis()
            return false
        }
        val startedAt = actorTurnStartedAt ?: return false
        if (currentTimeMillis() - startedAt < roomProperties.actionTimeoutMs) return false
        val player = map.getPlayerByPosition(timedActorPosition) ?: return false
        actorTurnStartedAt = null
        timedActorPosition = -1
        autoActOnTimeout(player)
        return true
    }

    private fun autoActOnTimeout(player: PokerPlayer) {
        val toCall = lastMaxBet - (player.currentBet ?: 0.0)
        if (toCall <= 0.0) {
            player.lastDecision = PokerDecision.CHECK
        } else {
            player.folded = true
            player.lastDecision = PokerDecision.FOLD
        }
        nextMove(player.userSession)
    }

    fun isGameStarted(): Boolean = gameStarted.get()

    private fun currentPhase(): PokerPhase {
        if (roundEnd.get()) return PokerPhase.SHOWDOWN
        return when (dealerHand.getCards().size) {
            3 -> PokerPhase.FLOP
            4 -> PokerPhase.TURN
            5 -> PokerPhase.RIVER
            else -> PokerPhase.PREFLOP
        }
    }

    private fun calculateHand(hand: CardDeck): String {
        val cards = hand.getCards()
        return PokerHand.fromList(cards).getHighestRank()
    }

    // in future should be somewhat transactional, don't apply any changes to stake or pot before check
    fun onPlayerDecision(
        userSession: PlayerSession,
        event: PokerPlayerDecisionEvent,
    ) {
        if (!started.get()) return
        val player = userSession.player as PokerPlayer
        if (!player.isAlive) return
        if (player.position != currentPosition) return
        val decision = enumValues<PokerDecision>().firstOrNull { it.name == event.inputId }
        if (decision == null) {
            sendFailure(userSession, FailureCode.INVALID_DECISION, "Unknown decision: ${event.inputId}")
            return
        }
        val amount = event.amount
        player.updateState(decision, amount)
    }

    private fun onDealerTurn() {
        // Sweep the round's bets into the pot before the next street.
        map.getPlayers().forEach {
            val bet = it.currentBet ?: 0.0
            if (bet > 0.0) {
                pot += bet
                it.currentBet = 0.0
            }
        }
        lastMaxBet = 0.0

        // Texas Hold'em улицы: 0 → flop (раздать 3), 3 → turn (1), 4 → river (1),
        // 5 → showdown. Раньше всегда раздавалась 1 карта, поэтому флоп выезжал
        // по одной карте за раз и в FE казалось, что «дали одну карту на префлопе».
        when (dealerHand.getCards().size) {
            0 -> repeat(3) { deck.dealCard(dealerHand) }
            3, 4 -> deck.dealCard(dealerHand)
            else -> {
                triggerShowdown()
                return
            }
        }

        // Постфлоп: первым ходит SB (== currentStartPlayer). В HU это
        // совпадает с button, но HU-постфлоп правило «BB acts first» тоже
        // здесь работает корректно: после префлопа BB сидит на (cs+1)%2 = cs+1,
        // SB — на cs. SB живой → currentPosition = SB. SB действует первым,
        // потом BB. Это формально расходится со стандартом HU postflop, где
        // BB acts first, — но поведение «button last» сохраняется (button=SB
        // acts after BB не выйдет: только два игрока). Для 3+ всё стандартно.
        // TODO(poker): для строгой HU postflop-семантики первый ходит BB.
        resetCurrentPositionForNewRound()

        // Никто не может действовать (все оставшиеся в раздаче — all-in) —
        // дораздаём оставшиеся улицы и идём в showdown. Без этого FE-актор
        // встаёт на all-in игрока с пустым availableActions и цикл замирает.
        if (!hasActiveActor()) {
            onDealerTurn()
        }
    }

    private fun hasActiveActor(): Boolean = map.getPlayers().any { !it.folded && !it.allin }

    private fun resetCurrentPositionForNewRound() {
        val playersCount = map.getPlayers().size
        if (playersCount == 0) return
        val first = firstAliveFrom(currentStartPlayer, playersCount)
        roundFirstActor = first
        currentPosition = first
    }

    // Рейз перезапускает круг торгов: action завершается, когда обратно дойдёт
    // до рейзера, а не до изначального firstActor. Без этого после раннего
    // call'а + поздний raise круг считался по старому маркеру и action делал
    // лишний оборот, возвращаясь на самого рейзера (HU postflop, 3+ постфлоп).
    internal fun markRaiser(position: Int) {
        roundFirstActor = position
    }

    private fun triggerShowdown() {
        if (roundEnd.get()) return
        // Свести ставки последнего круга в банк. Через nextMove → triggerShowdown
        // (все сфолдили, кроме одного) последняя улица в onDealerTurn не проходит,
        // поэтому её ставки иначе «висят» перед игроками. На distribution не
        // влияет (она считает по totalContribution) — это чисто для отображения:
        // FE покажет собранный pot в центре, а не зависшие фишки у мест.
        map.getPlayers().forEach {
            val bet = it.currentBet ?: 0.0
            if (bet > 0.0) {
                pot += bet
                it.currentBet = 0.0
            }
        }
        lastMaxBet = 0.0
        val nonFolded = map.getPlayers().filter { !it.folded }
        val canEvaluate = (dealerHand.getCards().size + 2) >= 5
        val contestants =
            map
                .getPlayers()
                .filter { it.totalContribution > 0.0 }
                .map { player ->
                    val hand =
                        when {
                            player.folded -> null
                            nonFolded.size == 1 -> uncontestedHand
                            canEvaluate -> evaluateBest(player)
                            else -> uncontestedHand
                        }
                    PokerContestant(player.id, player.totalContribution, hand)
                }
        val distribution = PokerSidePotDistribution.distribute(contestants)
        applyPayouts(distribution)
        pot = 0.0
        broadcastShowdown(distribution, canEvaluate && nonFolded.size > 1)
        roundEnd.set(true)
        // Showdown: актора больше нет — гасим турн-таймер, чтобы turnDeadlineEpochMs
        // не висел стейл-значением в reveal-окне (checkTurnTimeout тут уже не бежит).
        actorTurnStartedAt = null
        timedActorPosition = -1
        // Открываем reveal-окно: doUpdate видит roundEnd=true и пока
        // (now - showdownEndedAt) < showdownRevealMs — НЕ шлёт GAME_ROOM_STATUS
        // и НЕ делает resetTable. Только UPDATE-ы (с post-showdown стеками,
        // phase=SHOWDOWN). По истечении окна — нормальный cleanup.
        showdownEndedAt = currentTimeMillis()
    }

    // Эксплицитная обёртка, чтобы её можно было замокать в тестах
    // (см. PokerHUDoubleAllInTest, который дёргает время руками через
    // VirtualTimeScheduler — `System.currentTimeMillis` оттуда не управляется).
    internal open fun currentTimeMillis(): Long = System.currentTimeMillis()

    private val uncontestedHand: PokerHand by lazy {
        PokerHand.fromString("2H 3D 4S 5C 7H")
    }

    private fun evaluateBest(player: PokerPlayer): PokerHand {
        val all = player.playerDeck.getCards() + dealerHand.getCards()
        return PokerHand.bestOf(all)
    }

    private fun applyPayouts(distribution: PokerDistribution) {
        distribution.payouts.forEach { (id, amount) ->
            val player = map.getPlayerById(id) ?: return@forEach
            player.stack += amount
        }
    }

    private fun broadcastShowdown(
        distribution: PokerDistribution,
        revealHands: Boolean,
    ) {
        val bestByPlayer: Map<Long, PokerHand?> =
            map.getPlayers().associate { player ->
                player.id to if (revealHands && !player.folded) evaluateBest(player) else null
            }
        val entries =
            map.getPlayers().map { player ->
                val payout = distribution.payouts[player.id] ?: 0.0
                val best = bestByPlayer[player.id]
                PokerShowdownEntry(
                    id = player.id,
                    payout = payout,
                    handCategory = best?.getHighestRank(),
                    handCards = best?.cards,
                    holeCards = if (revealHands && !player.folded) player.playerDeck.getCards() else null,
                )
            }
        val pots =
            distribution.pots.map {
                PokerShowdownSidePot(it.amount, it.eligibleIds, it.winnerIds)
            }
        // Highlight-set = карты лучших комбинаций всех победителей (по всем сайд-
        // потам), dedup. Пусто, если вскрытия не было (revealHands=false → best=null).
        val winnerIds = distribution.pots.flatMapTo(mutableSetOf()) { it.winnerIds }
        val boardHighlight =
            winnerIds
                .mapNotNull { bestByPlayer[it] }
                .flatMap { it.cards }
                .distinct()
        sendBroadcast(Message(SHOWDOWN_RESULT, PokerShowdownPack(entries, pots, boardHighlight)))
    }

    override fun update() = synchronized(this) { doUpdate() }

    // Лочимся на том же мониторе, что и onBuyIn / nextMove (через PokerPlayer.update),
    // чтобы тик не успевал прочитать playerDeck посередине initialDeal-а
    // (раньше первый UPDATE мог уехать с 0/1 hole-картой). Все мутации состояния
    // раунда — здесь же, так что serialization дешёвая.
    private fun doUpdate() {
        if (!gameStarted.get()) return
        if (!roundEnd.get()) {
            // Турн-таймер взводится/проверяется до рассылки: если актор просрочил
            // ход, авто-действие уже сдвинуло раздачу — пропускаем тик, следующий
            // (через loopRate) разошлёт актуальный стейт.
            if (checkTurnTimeout()) return
            for (currentPlayer in map.getPlayers()) {
                val newUpdate = collectUpdate(currentPlayer)
                val sessionId = currentPlayer.userSession.id
                val previous = lastUpdateBySession[sessionId]
                if (previous == null || previous.data != newUpdate.data) {
                    send(currentPlayer.userSession, newUpdate)
                    lastUpdateBySession[sessionId] = newUpdate
                }
            }
        } else {
            // Reveal-фаза: после triggerShowdown окно showdownRevealMs.
            // Пока оно открыто — шлём только UPDATE (post-showdown state с
            // 5 картами и обновлёнными стеками), но GAME_ROOM_STATUS+resetTable
            // откладываем. Иначе FE при loopRate=300ms успевает получить
            // GAME_ROOM_STATUS и отменить reveal-таймеры (350ms) до их срабатывания.
            val revealedAt = showdownEndedAt
            val revealStillOpen =
                revealedAt != null &&
                    (currentTimeMillis() - revealedAt) < roomProperties.showdownRevealMs
            for (currentPlayer in map.getPlayers()) {
                send(currentPlayer.userSession, collectUpdate(currentPlayer))
                if (!revealStillOpen) {
                    send(currentPlayer.userSession, Message(GAME_ROOM_STATUS))
                }
            }
            if (!revealStillOpen) {
                showdownEndedAt = null
                resetTable()
                // resetTable переводит busted-игроков в observer (boughtIn=false →
                // needsRebuy=true) и при <2 профинансированных ставит игру на паузу
                // (gameStarted=false). UPDATE выше ушёл ДО resetTable, т.е. с
                // needsRebuy=false, а следующий тик doUpdate выйдет на gameStarted-
                // гарде — busted-игрок в хедз-апе так и не увидел бы re-buy подсказку
                // до перезахода. Досылаем финальный post-reset UPDATE, когда игра
                // встала на паузу; при active>=2 раздача продолжается и стейт
                // доставит обычный тик.
                if (!gameStarted.get()) {
                    for (currentPlayer in map.getPlayers()) {
                        val update = collectUpdate(currentPlayer)
                        send(currentPlayer.userSession, update)
                        lastUpdateBySession[currentPlayer.userSession.id] = update
                    }
                }
            }
        }
    }

    // main game cycle driver
    fun nextMove(userSession: PlayerSession) {
        if (!started.get()) return
        val player = userSession.player as PokerPlayer
        if (!player.isAlive) return

        // Only one non-folded player left — they collect the whole pot without revealing cards.
        val active = map.getPlayers().filter { !it.folded }
        if (active.size <= 1) {
            triggerShowdown()
            return
        }

        // Все non-folded игроки уже all-in — действовать некому, action-круг
        // не закроется через currentLastPlayer (последний all-in мог сдвинуть
        // roundFirstActor через markRaiser, и currentLastPlayer теперь указывает
        // на уже-олл-ин-нутого оппонента). Идём прямо в onDealerTurn, который
        // дораздаст оставшиеся улицы и через рекурсию уйдёт в showdown.
        // Раньше advanceToNextActivePosition в такой ситуации не находил
        // активного игрока и молча выходил, оставляя стол замороженным
        // (HU postflop both-all-in с full-raise второго → freeze на флопе/тёрне).
        if (!hasActiveActor()) {
            return onDealerTurn()
        }

        val playersCount = map.getPlayers().size
        // Круг торгов закрывается, когда все ставки уравнены И action
        // возвращается к первому ходившему в круге (== последнему агрессору
        // после рейза). Раньше "последний в круге" вычислялся как
        // (roundFirstActor-1) БЕЗ учёта folded/all-in игроков: если этот игрок
        // сфолдил (частый случай после ре-рейза — агрессор сидит сразу за
        // выбывшим), то currentPosition никогда с ним не совпадал, круг не
        // закрывался и стол замерзал — всех оставшихся активных бесконечно
        // просили "чекать". Теперь смотрим на следующего РЕАЛЬНО активного
        // игрока: если им оказался бы roundFirstActor (или активных больше
        // не осталось) — круг закрыт.
        if (allBetsValid()) {
            val nextActor = nextActiveActorAfter(currentPosition, playersCount)
            if (nextActor == null || nextActor == roundFirstActor) {
                return onDealerTurn()
            }
        }
        // advance to the next active (not folded, not all-in) player
        advanceToNextActivePosition(playersCount)
    }

    /** Следующая позиция активного (не folded, не all-in) игрока строго после
     *  [from], обходя стол по кругу. null — если другого активного игрока в
     *  круге не осталось (только сам [from] либо вообще никого). */
    private fun nextActiveActorAfter(
        from: Int,
        n: Int,
    ): Int? {
        if (n == 0) return null
        for (i in 1 until n) {
            val pos = (from + i) % n
            val candidate = map.getPlayerByPosition(pos)
            if (candidate != null && !candidate.folded && !candidate.allin) return pos
        }
        return null
    }

    private fun advanceToNextActivePosition(playersCount: Int) {
        if (playersCount == 0) return
        var next = currentPosition
        repeat(playersCount) {
            next = (next + 1) % playersCount
            val candidate = map.getPlayerByPosition(next)
            if (candidate != null && !candidate.folded && !candidate.allin) {
                currentPosition = next
                return
            }
        }
        // no active players left — keep currentPosition as-is, round resolution handled elsewhere
    }

    // check if all players placed their bets and everyone chose call, all-in or fold
    fun allBetsValid(): Boolean {
        for (player in map.getPlayers()) {
            if (player.currentBet != lastMaxBet) {
                if (!player.folded && !player.allin) return false
            }
        }
        return true
    }

    fun onBuyIn(
        userSession: PlayerSession,
        event: BetEvent,
    ) {
        val player = userSession.player as PokerPlayer
        val buyIn = event.bet
        // Идемпотентный путь для повторных BET-ов (FE авто-buy-in после reattach):
        // вместо BET_FAILURE отвечаем INFO с актуальным балансом, чтобы клиент
        // не выводил красный тост на штатном refresh-е.
        if (player.boughtIn) {
            return
        }
        if (buyIn <= 0.0) {
            sendBetFailure(userSession, FailureCode.INVALID_BET, "Buy-in must be positive")
            return
        }
        if (buyIn < minBuyIn) {
            sendBetFailure(userSession, FailureCode.BET_BELOW_MIN, "Buy-in below table minimum $minBuyIn")
            return
        }
        maxBuyIn?.let { cap ->
            if (buyIn > cap) {
                sendBetFailure(userSession, FailureCode.INVALID_BET, "Buy-in above table maximum $cap")
                return
            }
        }
        if (buyIn > player.balance) {
            sendBetFailure(userSession, FailureCode.INSUFFICIENT_FUNDS, "Insufficient balance")
            return
        }
        // Buy-in от нескольких сессий приходит по разным WS-стримам, у Reactor
        // нет per-room single-thread гарантии — без synchronized два BET-а от
        // P1/P2 гоняются за чтением `boughtIn` соседа и либо оба ловят false и
        // НЕ стартуют игру, либо оба стартуют (двойной initialTurn). Лочимся на
        // комнате, потому что весь mutable state раунда — здесь же.
        val startNow: Boolean
        synchronized(this) {
            if (player.boughtIn) return
            player.bet = buyIn
            player.balance -= buyIn
            player.stack = buyIn
            player.boughtIn = true
            // Re-buy observer-а: отменяем auto-leave таймер — игрок остаётся.
            observerTimers.remove(player.id)?.dispose()
            if (gameStarted.get()) {
                // Late buy-in: seat is funded, but the player sits out the in-progress
                // round. resetTable() unfolds them at round end so they play next hand.
                player.folded = true
                startNow = false
            } else {
                startNow = map.getPlayers().all { it.boughtIn } && gameStarted.compareAndSet(false, true)
            }
        }
        userSession.userId?.let { uid ->
            ledgerService
                ?.applyDelta(uid, UUID.randomUUID(), -buyIn, BalanceLedgerReason.POKER_BUY_IN)
                ?.subscribe()
        }
        if (startNow) {
            initialTurn()
        }
    }

    private fun resetBets() {
        map.getPlayers().forEach {
            pot += it.currentBet!!
            it.currentBet = 0.00
        }
    }

    private fun resetTable() {
        // Игроки, помеченные на выход в середине прошлой раздачи (LEAVE/grace-
        // expiry в активном раунде, см. onDisconnect), снимаются здесь —
        // в активном раунде удалять было нельзя из-за позиционной арифметики.
        map
            .getPlayers()
            .filter { it.leaving }
            .toList()
            .forEach { removeSeat(it) }

        // Busted-игроки (был профинансирован, стек кончился) НЕ кикаются —
        // переводятся в observer: остаются за столом без денег, не получают
        // карт (boughtIn=false → folded ниже), не блокируют раздачу, и им даётся
        // OBSERVER_GRACE_MS на re-buy. Раньше здесь был мгновенный
        // GAME_ROOM_CLOSE + removePlayer. На момент resetTable раздача уже
        // закончилась, поэтому stale-флаг `allin` тут не важен — bust по stack<=0.
        map.getPlayers().filter { it.boughtIn && it.stack <= 0.0 }.toList().forEach {
            it.boughtIn = false
            startObserverGrace(it)
        }
        // Окно showdown reveal закрыто к моменту resetTable — гасим маркер.
        showdownEndedAt = null

        // Clear table cards
        dealerHand.clear()
        map.getPlayers().forEach { it.playerDeck.clear() }
        deck = CardDeck(roomProperties.deckStacks)

        // Reset round-level state. pot обнуляется именно здесь (а не в
        // triggerShowdown), чтобы во время reveal-окна FE видел собранный банк
        // в центре и успел проиграть «pot → winner».
        pot = 0.00
        lastMaxBet = 0.00
        currentPosition = 0
        dealerCardsCount = 0
        roundEnd.set(false)
        dealerTurn.set(false)

        // Reset per-player round state
        map.getPlayers().forEach {
            it.currentBet = 0.00
            it.lastBet = null
            it.folded = false
            it.allin = false
            it.madeDecision = false
            it.lastDecision = PokerDecision.NONE
            it.totalContribution = 0.0
        }
        // Late-joiners without buy-in stay seated but cannot play until they fund.
        map.getPlayers().filter { !it.boughtIn }.forEach { it.folded = true }
        // Players in grace-period keep their seat+stack but sit out future rounds until reattach.
        map.getPlayers().filter { it.disconnected }.forEach { it.folded = true }

        // Кнопка дилера сдвигается на +1 ОДИН раз в конце раздачи (а не на
        // каждый street, как раньше: для N=3 это случайно работало, для N=4+
        // button «прыгал» на +4 позиций за раздачу).
        val n = map.getPlayers().size
        if (n >= 2) {
            currentStartPlayer = (currentStartPlayer + 1) % n
        }
        // Если в комнате осталось ≥2 живых игроков с buy-in — раздаём следующий раунд.
        val active = map.getPlayers().count { it.boughtIn && !it.folded }
        if (active >= 2) {
            initialTurn()
        } else {
            // Меньше двух активных — игра приостанавливается до следующего buy-in.
            gameStarted.set(false)
        }
    }

    override fun onPlayerInfoRequest(userSession: PlayerSession) {
        send(
            userSession,
            Message(
                INFO,
                InfoPack(
                    (userSession.player as PokerPlayer).getInfoPack(),
                    roomProperties.loopRate,
                    map.alivePlayers(),
                ),
            ),
        )
    }

    override fun onDestroy(userSessions: List<PlayerSession>) {
        userSessions.forEach { userSession: PlayerSession ->
            map.removePlayer(
                userSession.player as PokerPlayer,
            )
        }
        super.onDestroy(userSessions)
    }

    override fun onClose(userSession: PlayerSession) {
        send(userSession, Message(GAME_ROOM_CLOSE))
        super.onClose(userSession)
    }

    override fun onGraceStart(userSession: PlayerSession) {
        val player = userSession.player as? PokerPlayer ?: return
        player.disconnected = true
        // НЕ фолдим на старте грейса — даже если сейчас ход игрока. Грейс это окно
        // на ребаунд (refresh страницы ~1с); фолд-на-своём-ходу превращал любой
        // refresh в форсированный фолд → оппонент забирал банк. Теперь ход просто
        // ждёт: если игрок вернётся в пределах disconnectGraceMs (onReattach
        // снимет disconnected) — доигрывает свой ход; если не вернётся —
        // grace-expiry идёт в onDisconnect, который и сделает auto-fold + nextMove.
        // Стол при этом висит максимум disconnectGraceMs (60s), а не до
        // ACTION_TIMEOUT — turn-deadline лишь страхует совсем брошенный ход.
    }

    override fun onReattach(
        oldSession: PlayerSession,
        newSession: PlayerSession,
    ) {
        super.onReattach(oldSession, newSession)
        val player = newSession.player as? PokerPlayer ?: return
        player.userSession = newSession
        player.disconnected = false
        lastUpdateBySession.remove(oldSession.id)

        // Если игра приостановилась пока игрок был в grace (active<2 в resetTable
        // из-за disconnect→folded), и теперь у нас снова достаточно живых
        // игроков — снимаем disconnect-фолд и запускаем следующую раздачу.
        // Voluntary folds на этом этапе уже сброшены в resetTable.forEach,
        // так что player.folded=true тут означает именно "отсижен disconnect-ом".
        if (!gameStarted.get()) {
            player.folded = false
            val active = map.getPlayers().count { it.boughtIn && !it.folded && !it.disconnected }
            if (active >= roomProperties.minPlayers) {
                gameStarted.set(true)
                initialTurn()
            }
        }
    }

    override fun onDisconnect(userSession: PlayerSession): PlayerSession {
        val player = userSession.player as? PokerPlayer
        if (player != null) {
            observerTimers.remove(player.id)?.dispose()
            val midRound = gameStarted.get() && !roundEnd.get()
            if (midRound) {
                if (!player.folded) {
                    player.folded = true
                    nextMove(userSession)
                }
                cashOutOnDisconnect(userSession, player)
                // Seat нельзя снять прямо сейчас — позиции это индексы по модулю
                // размера стола, удаление в активном раунде их разрежает и ломает
                // арифметику кругов. Помечаем; resetTable снимет на границе раздачи.
                player.leaving = true
            } else {
                // Showdown / пауза — удаление безопасно (resetTable и так удаляет
                // здесь). Раньше seat не снимался вовсе: busted-игрок, приславший
                // LEAVE на SHOWDOWN, оставался «зомби-сидящим» в players[] до
                // следующего resetTable (а в HU его и не было — игра вставала).
                cashOutOnDisconnect(userSession, player)
                removeSeat(player)
            }
        }
        return super.onDisconnect(userSession)
    }

    private fun removeSeat(player: PokerPlayer) {
        observerTimers.remove(player.id)?.dispose()
        lastUpdateBySession.remove(player.userSession.id)
        map.removePlayer(player)
    }

    // Busted-игрок переведён в observer (resetTable): даём окно на re-buy, после
    // чего комната сама высаживает его тем же путём, что и явный LEAVE
    // (webSocketSessionService.onPlayerLeave → onClose+onDisconnect+clearBinding),
    // — единый код-путь, без дублирования GAME_ROOM_CLOSE/seat-removal. re-buy
    // отменяет таймер в onBuyIn; повторный bust — перезапускает (dispose выше).
    private fun startObserverGrace(player: PokerPlayer) {
        observerTimers.remove(player.id)?.dispose()
        observerTimers[player.id] =
            scheduleCancellable({
                val shouldLeave =
                    synchronized(this) {
                        val stillScheduled = observerTimers[player.id] != null
                        if (stillScheduled && !player.boughtIn && map.getPlayerById(player.id) != null) {
                            observerTimers.remove(player.id)
                            true
                        } else {
                            false
                        }
                    }
                if (shouldLeave) webSocketSessionService.onPlayerLeave(player.userSession)
            }, roomProperties.observerGraceMs)
    }

    private fun cashOutOnDisconnect(
        userSession: PlayerSession,
        player: PokerPlayer,
    ) {
        if (!player.boughtIn) return
        val remaining = player.stack
        player.stack = 0.0
        player.boughtIn = false
        if (remaining <= 0.0) return
        player.balance += remaining
        val uid = userSession.userId ?: return
        ledgerService
            ?.applyDelta(uid, UUID.randomUUID(), remaining, BalanceLedgerReason.POKER_CASH_OUT)
            ?.subscribe()
    }
}
