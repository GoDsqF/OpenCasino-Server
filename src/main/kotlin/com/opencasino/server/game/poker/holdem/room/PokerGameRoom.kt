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
import com.opencasino.server.service.shared.FailureCode
import com.opencasino.server.service.shared.PokerDecision
import com.opencasino.server.service.shared.PokerPhase
import com.opencasino.server.user.BalanceLedgerReason
import com.opencasino.server.user.BalanceLedgerService
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

    // explains itself
    var pot: Double = 0.00
    var lastMaxBet: Double = 0.00

    // explains itself too
    val smallBlind: Double = bet / 2
    val bigBlind: Double = bet

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

    // game status control
    private val started = AtomicBoolean(false)
    private val gameStarted = AtomicBoolean(false)
    private val dealerTurn = AtomicBoolean(false)
    private val roundEnd = AtomicBoolean(false)

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

    fun addLatePlayer(userSession: PlayerSession) {
        val player = userSession.player as PokerPlayer
        map.addPlayer(player)
        super.onRoomCreated(listOf(userSession))
        send(
            userSession,
            Message(
                GAME_ROOM_JOIN_SUCCESS,
                GameSettingsPack(gameRoomId.toString(), roomProperties.loopRate),
            ),
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
            Message(
                GAME_ROOM_JOIN_SUCCESS,
                GameSettingsPack(
                    gameRoomId.toString(),
                    roomProperties.loopRate,
                ),
            ),
        )

        schedulePeriodically(
            this,
            roomProperties.initDelay,
            roomProperties.loopRate,
        )

        schedule(
            { roomService.onGameEnd(this) },
            roomProperties.endDelay + roomProperties.startDelay,
        )

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
        sendBroadcast(
            Message(
                GAME_START,
                RoomPack(
                    ZonedDateTime
                        .now(ZoneId.of("Europe/Moscow"))
                        .plus(roomProperties.endDelay, ChronoUnit.MILLIS)
                        .toInstant()
                        .toEpochMilli(),
                    gameRoomId.toString(),
                ),
            ),
        )
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
            ),
        )
    }

    fun actorPosition(): Int? = if (roundEnd.get()) null else currentPosition

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
    }

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
    }

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
        val entries =
            map.getPlayers().map { player ->
                val payout = distribution.payouts[player.id] ?: 0.0
                val best = if (revealHands && !player.folded) evaluateBest(player) else null
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
        sendBroadcast(Message(SHOWDOWN_RESULT, PokerShowdownPack(entries, pots)))
    }

    override fun update() = synchronized(this) { doUpdate() }

    // Лочимся на том же мониторе, что и onBuyIn / nextMove (через PokerPlayer.update),
    // чтобы тик не успевал прочитать playerDeck посередине initialDeal-а
    // (раньше первый UPDATE мог уехать с 0/1 hole-картой). Все мутации состояния
    // раунда — здесь же, так что serialization дешёвая.
    private fun doUpdate() {
        if (!gameStarted.get()) return
        if (!roundEnd.get()) {
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
            for (currentPlayer in map.getPlayers()) {
                send(
                    currentPlayer.userSession,
                    collectUpdate(currentPlayer),
                )
                send(
                    currentPlayer.userSession,
                    Message(
                        GAME_ROOM_STATUS,
                    ),
                )
            }
            resetTable()
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

        val playersCount = map.getPlayers().size
        // Круг закончен, когда только что отыгравший — это последний в круге
        // (т.е. предыдущая позиция от roundFirstActor) и все ставки уравнены.
        // Раньше last рассчитывался от currentStartPlayer (= позиция SB),
        // что давало UTG-1 = BB для cs=0, N=3 — формально верно, но если cs
        // менялся в середине раздачи (старая ротация +1 на street), маркер
        // съезжал и UTG получал лишний ход.
        val currentLastPlayer = (roundFirstActor - 1 + playersCount) % playersCount

        if (currentPosition == currentLastPlayer && allBetsValid()) {
            return onDealerTurn()
        }
        // advance to the next active (not folded, not all-in) player
        advanceToNextActivePosition(playersCount)
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
        if (buyIn < roomProperties.buyIn) {
            sendBetFailure(userSession, FailureCode.BET_BELOW_MIN, "Buy-in below table minimum ${roomProperties.buyIn}")
            return
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
        // Drop players who have no stack left and aren't all-in (busted out)
        val broke = map.getPlayers().filter { it.stack <= 0.0 && !it.allin }.toList()
        broke.forEach { map.removePlayer(it) }

        // Clear table cards
        dealerHand.clear()
        map.getPlayers().forEach { it.playerDeck.clear() }
        deck = CardDeck(roomProperties.deckStacks)

        // Reset round-level state
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
        // Грейс — это окно, в которое клиент может ребаундить (refresh страницы
        // занимает <1с при disconnectGraceMs ≈ 15с). Раньше мы здесь сразу
        // фолдили игрока, из-за чего любой refresh = форсированный фолд →
        // оппонент забирал банк → resetTable стартовал новую раздачу. С точки
        // зрения юзера «раунд сбросился». Теперь фолдим только если СЕЙЧАС
        // его ход — иначе остальные за столом висели бы в ожидании action-а.
        // Если refresh не на его ходу, raund продолжается без участия игрока,
        // он реконнектится и доигрывает (либо доходит до showdown без него,
        // потому что мы не аукционируем за него ставки).
        val isPlayersTurn = gameStarted.get() && !roundEnd.get()
        val canFold = isPlayersTurn && !player.folded && player.position == currentPosition
        if (canFold) {
            player.folded = true
            nextMove(userSession)
        }
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
    }

    override fun onDisconnect(userSession: PlayerSession): PlayerSession {
        val player = userSession.player as? PokerPlayer
        if (player != null) {
            if (gameStarted.get() && !roundEnd.get() && !player.folded) {
                player.folded = true
                nextMove(userSession)
            }
            cashOutOnDisconnect(userSession, player)
        }
        return super.onDisconnect(userSession)
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
