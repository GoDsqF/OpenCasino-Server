package com.opencasino.server.game.blackjack.room

import com.opencasino.server.config.*
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.BlackjackPlayerDecisionEvent
import com.opencasino.server.game.blackjack.map.BlackjackMap
import com.opencasino.server.game.blackjack.model.BlackjackCondition
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.model.Rank
import com.opencasino.server.network.pack.blackjack.info.InfoPack
import com.opencasino.server.network.pack.blackjack.shared.BlackjackConditionPack
import com.opencasino.server.network.pack.blackjack.shared.GameSettingsPack
import com.opencasino.server.network.pack.blackjack.shared.RoomPack
import com.opencasino.server.network.pack.blackjack.update.GameUpdatePack
import com.opencasino.server.network.pack.shared.DealerUpdatePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.impl.BlackjackRoomServiceImpl
import com.opencasino.server.service.shared.BlackjackDecision
import com.opencasino.server.service.shared.ClientState
import com.opencasino.server.service.shared.FailureCode
import com.opencasino.server.user.BalanceLedgerReason
import com.opencasino.server.user.BalanceLedgerService
import reactor.core.scheduler.Scheduler
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

open class BlackjackGameRoom(
    val map: BlackjackMap,
    gameRoomId: UUID,
    roomService: BlackjackRoomServiceImpl,
    webSocketSessionService: WebSocketSessionService,
    schedulerService: Scheduler,
    val gameProperties: GameProperties,
    val roomProperties: BlackjackRoomProperties,
    private val ledgerService: BalanceLedgerService,
) : AbstractBlackjackGameRoom(gameRoomId, schedulerService, roomService, webSocketSessionService) {
    private val testing = AtomicBoolean(false)

    private val lastUpdateBySession: MutableMap<String, Message> = HashMap()
    private val started = AtomicBoolean(false)
    private val gameStarted = AtomicBoolean(false)

    // Фаза добора дилера: после того как все руки игрока закрыты, дилер берёт
    // по одной карте за тик (loopRate), а не мгновенно одним пакетом — это даёт
    // FE покарточную deal-in анимацию (как при HIT-е игрока). Сбрасывается, когда
    // дилер достиг >= 17 и зафиксирован handConditions.
    @Volatile private var dealerDrawing = false

    private var handConditions: List<BlackjackCondition>? = null

    // Турн-таймер: момент, когда у игрока открылось окно хода. Перевзводится, когда
    // он сам сходил (новое окно) и гасится между руками/раундами. По истечении
    // actionTimeoutMs сервер сам делает STAND текущей руки. turnDeadlineEpochMs =
    // это + actionTimeoutMs. null = ходить сейчас некому.
    @Volatile private var actorTurnStartedAt: Long? = null

    private val combiner: Map<Rank, Int> =
        mapOf(
            Rank.C2 to 2,
            Rank.C3 to 3,
            Rank.C4 to 4,
            Rank.C5 to 5,
            Rank.C6 to 6,
            Rank.C7 to 7,
            Rank.C8 to 8,
            Rank.C9 to 9,
            Rank.C10 to 10,
            Rank.CJ to 10,
            Rank.CQ to 10,
            Rank.CK to 10,
            Rank.CA to 11,
        )

    var deck = CardDeck(roomProperties.deckStacks)

    var dealerHand = CardDeck()

    private var currentRoundId: UUID = UUID.randomUUID()

    private fun initialDeal() {
        currentRoundId = UUID.randomUUID()
        val players = map.getPlayers()
        for (player in players) {
            val hand = player.currentHand()
            hand.bet = player.bet
            deck.dealCards(2, hand.deck)
        }
        deck.dealCard(dealerHand, true)
        deck.dealCard(dealerHand, false)
        gameStarted.set(true)
        initialCheck()
    }

    private fun initialCheck(): List<BlackjackCondition>? {
        val player = map.getPlayers().first()
        val playerSum = calculateScore(player.currentHand().deck)
        val dealerSum = calculateScore(dealerHand)

        val outcome: BlackjackCondition? =
            when {
                dealerSum == 21 && playerSum != 21 -> BlackjackCondition.DealerBlackjack
                playerSum == 21 -> BlackjackCondition.PlayerWinBlackjack
                else -> null
            }

        if (outcome != null) {
            player.currentHand().resolved = true
            handConditions = listOf(outcome)
        }
        return handConditions
    }

    override fun onRoomCreated(userSessions: List<PlayerSession>) {
        if (userSessions.isNotEmpty()) {
            userSessions.forEach {
                val player = it.player as BlackjackPlayer
                player.position = userSessions.size
                map.addPlayer(player)
            }
        }

        super.onRoomCreated(userSessions)

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

    private fun collectUpdate(player: BlackjackPlayer): Message {
        if (player.isAlive) player.update()
        val updatePack = player.getPrivateUpdatePack()
        val playerUpdatePackList =
            map
                .getPlayers()
                .map { it.getUpdatePack(isYou = it.id == player.id) }

        val dealerUpdatePack = DealerUpdatePack(dealerHand.toPublicView())

        return Message(
            UPDATE,
            GameUpdatePack(
                updatePack,
                playerUpdatePackList,
                dealerUpdatePack,
                turnDeadlineEpochMs(),
                clientStateFor(player),
            ),
        )
    }

    internal open fun currentTimeMillis(): Long = System.currentTimeMillis()

    private fun turnDeadlineEpochMs(): Long? = actorTurnStartedAt?.let { it + roomProperties.actionTimeoutMs }

    private fun clientStateFor(player: BlackjackPlayer): ClientState =
        when {
            handConditions != null -> ClientState.SHOWDOWN
            !gameStarted.get() -> ClientState.AWAITING_BUY_IN
            player.canAct() -> ClientState.AWAITING_TURN
            else -> ClientState.IN_ROUND
        }

    // Перевзводит окно хода и авто-STAND-ит текущую руку, если игрок просрочил.
    // STAND идёт штатным decision-путём (updateState → обрабатывается в update()
    // этого же тика), поэтому отдельной resolve-логики не дублируем.
    private fun enforceTurnTimeout() {
        val player = map.getPlayers().firstOrNull()
        if (player == null || !player.canAct() || player.hasPendingDecision()) {
            actorTurnStartedAt = null
            return
        }
        val startedAt = actorTurnStartedAt
        if (startedAt == null) {
            actorTurnStartedAt = currentTimeMillis()
            return
        }
        if (currentTimeMillis() - startedAt >= roomProperties.actionTimeoutMs) {
            actorTurnStartedAt = null
            player.updateState(BlackjackDecision.STAND)
        }
    }

    override fun update() {
        if (!gameStarted.get()) return
        if (handConditions != null) {
            dealerHand.openCards()
            settleRound()
            val conditions = handConditions!!.map { it.name }
            for (currentPlayer in map.getPlayers()) {
                send(
                    currentPlayer.userSession,
                    collectUpdate(currentPlayer),
                )
                send(
                    currentPlayer.userSession,
                    Message(
                        GAME_ROOM_STATUS,
                        BlackjackConditionPack(conditions),
                    ),
                )
            }
            reset()
            return
        }
        if (dealerDrawing) {
            stepDealerDraw()
        } else {
            // Авто-STAND по таймауту обрабатывается в collectUpdate→player.update()
            // ниже тем же тиком (updateState просто ставит флаг).
            enforceTurnTimeout()
        }
        broadcastUpdates()
    }

    private fun broadcastUpdates() {
        for (currentPlayer in map.getPlayers()) {
            val newUpdate = collectUpdate(currentPlayer)
            val sessionId = currentPlayer.userSession.id
            val previous = lastUpdateBySession[sessionId]
            if (previous == null || previous.data != newUpdate.data) {
                send(currentPlayer.userSession, newUpdate)
                lastUpdateBySession[sessionId] = newUpdate
            }
        }
    }

    // Один шаг добора дилера за тик: берём ровно одну карту, пока < 17. Когда
    // достигли 17+ — фиксируем исход; терминальная ветка update() уже следующим
    // тиком разошлёт UPDATE(SHOWDOWN) + GAME_ROOM_STATUS. broadcastUpdates() в
    // том же тике отправит добранную карту, так что FE её анимирует.
    private fun stepDealerDraw() {
        if (calculateScore(dealerHand) < 17) {
            deck.dealCard(dealerHand)
        }
        if (calculateScore(dealerHand) >= 17) {
            dealerDrawing = false
            handConditions = resolveConditions()
        }
    }

    private fun payoutFor(
        condition: BlackjackCondition,
        bet: Double,
    ): Double =
        when (condition) {
            BlackjackCondition.PlayerWin -> bet * 2.0
            BlackjackCondition.PlayerWinBlackjack -> bet * 2.5
            BlackjackCondition.DealerWin, BlackjackCondition.DealerBlackjack -> 0.0
            BlackjackCondition.Draw, BlackjackCondition.None -> bet
        }

    private fun settleRound() {
        val conds = handConditions ?: return
        for (player in map.getPlayers()) {
            val totalBet = player.hands.sumOf { it.bet }
            val totalPayout = player.hands.zip(conds).sumOf { (h, c) -> payoutFor(c, h.bet) }
            player.balance += totalPayout
            val delta = totalPayout - totalBet
            val userId = player.userSession.userId ?: continue
            ledgerService
                .applyDelta(userId, currentRoundId, delta, BalanceLedgerReason.BLACKJACK_ROUND)
                .subscribe()
        }
    }

    override fun onPlayerInfoRequest(userSession: PlayerSession) {
        send(
            userSession,
            Message(
                INFO,
                InfoPack(
                    (userSession.player as BlackjackPlayer).getInfoPack(),
                    roomProperties.loopRate,
                    map.alivePlayers(),
                ),
            ),
        )
    }

    fun onPlayerHit(player: BlackjackPlayer) {
        val hand = player.currentHand()
        val sum = calculateScore(hand.deck)
        if (sum >= 21) {
            hand.resolved = true
            onHandResolved(player)
        }
    }

    fun onHandResolved(player: BlackjackPlayer) {
        if (player.advanceToNextHand()) return
        beginDealerPlay()
    }

    fun onSplitCompleted(player: BlackjackPlayer) {
        for (hand in player.hands) {
            if (calculateScore(hand.deck) == 21) hand.resolved = true
        }
        if (player.hands.all { it.resolved }) {
            beginDealerPlay()
        } else {
            player.advanceToNextHand()
        }
    }

    // Переход в фазу добора: раскрываем hole-карту (FE играет flip) и гасим окно
    // хода. Сам добор идёт по карте за тик в stepDealerDraw(); исход (handConditions)
    // фиксируется там же, когда дилер достиг 17+.
    private fun beginDealerPlay() {
        dealerHand.openCards()
        actorTurnStartedAt = null
        dealerDrawing = true
    }

    private fun resolveConditions(): List<BlackjackCondition> {
        val player = map.getPlayers().first()
        val dealerSum = calculateScore(dealerHand)
        return player.hands.map { hand ->
            val playerSum = calculateScore(hand.deck)
            when {
                playerSum > 21 -> BlackjackCondition.DealerWin
                dealerSum > 21 -> BlackjackCondition.PlayerWin
                dealerSum < playerSum -> BlackjackCondition.PlayerWin
                dealerSum > playerSum -> BlackjackCondition.DealerWin
                else -> BlackjackCondition.Draw
            }
        }
    }

    // Синхронный добор + резолв для дисконнекта: тиков больше не будет (комната
    // закрывается), исход надо посчитать здесь и сейчас, без покадровой раздачи.
    private fun playDealerToCompletionSync() {
        dealerHand.openCards()
        while (calculateScore(dealerHand) < 17) {
            deck.dealCard(dealerHand)
        }
        dealerDrawing = false
        handConditions = resolveConditions()
    }

    private fun calculateScore(hand: CardDeck): Int {
        val cards = hand.getCards()
        var score = cards.sumOf { combiner[it.rank]!! }
        var numAces = cards.count { it.rank == Rank.CA }

        while (score > 21 && numAces > 0) {
            score -= 10
            numAces--
        }

        return score
    }

    override fun onPlayerDecision(
        userSession: PlayerSession,
        event: BlackjackPlayerDecisionEvent,
    ) {
        if (!started.get()) return
        val player = userSession.player as BlackjackPlayer
        if (!player.isAlive) return
        val decision = enumValues<BlackjackDecision>().firstOrNull { it.name == event.inputId }
        if (decision == null) {
            sendFailure(userSession, FailureCode.INVALID_DECISION, "Unknown decision: ${event.inputId}")
            return
        }
        player.updateState(decision)
    }

    override fun onBet(
        userSession: PlayerSession,
        event: BetEvent,
    ) {
        if (gameStarted.get()) return
        val player = userSession.player as BlackjackPlayer
        val bet = event.bet
        if (bet <= 0.0) {
            sendBetFailure(userSession, FailureCode.INVALID_BET, "Bet must be positive")
            return
        }
        if (bet < roomProperties.minBet) {
            sendBetFailure(userSession, FailureCode.BET_BELOW_MIN, "Bet below minimum ${roomProperties.minBet}")
            return
        }
        if (bet > player.balance) {
            sendBetFailure(userSession, FailureCode.INSUFFICIENT_FUNDS, "Insufficient balance")
            return
        }
        player.bet = bet
        player.balance -= bet
        initialDeal()
    }

    private fun reset() {
        gameStarted.set(false)
        handConditions = null
        actorTurnStartedAt = null
        dealerDrawing = false
        map.getPlayers().forEach { it.resetForNewRound() }
        dealerHand = CardDeck()
        if (deck.getCards().size < roomProperties.reshuffleThreshold) {
            deck = CardDeck(roomProperties.deckStacks)
        }
        onGameStarted()
    }

    override fun onDestroy(userSessions: List<PlayerSession>) {
        userSessions.forEach { userSession: PlayerSession ->
            map.removePlayer(
                userSession.player as BlackjackPlayer,
            )
        }
        super.onDestroy(userSessions)
    }

    override fun onClose(userSession: PlayerSession) {
        send(userSession, Message(GAME_ROOM_CLOSE))
        super.onClose(userSession)
    }

    override fun onReattach(
        oldSession: PlayerSession,
        newSession: PlayerSession,
    ) {
        super.onReattach(oldSession, newSession)
        val player = newSession.player as? BlackjackPlayer ?: return
        player.userSession = newSession
        lastUpdateBySession.remove(oldSession.id)
    }

    override fun onDisconnect(userSession: PlayerSession): PlayerSession {
        if (gameStarted.get() && handConditions == null) {
            val player = userSession.player as? BlackjackPlayer
            if (player != null && player.hands.isNotEmpty()) {
                player.hands.forEach { it.resolved = true }
                playDealerToCompletionSync()
                settleRound()
            }
        }
        return super.onDisconnect(userSession)
    }
}
