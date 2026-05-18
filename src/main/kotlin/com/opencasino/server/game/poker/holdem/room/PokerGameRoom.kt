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

    //can i delete this later?
    var minLimit: Double? = null
    var maxLimit: Double? = null
    //
    var betType: PokerBetType = PokerBetType.PotLimit
    var bet: Double = 100.00

    //explains itself
    var pot: Double = 0.00
    var lastMaxBet: Double = 0.00
    //explains itself too
    val smallBlind: Double = bet / 2
    val bigBlind: Double = bet

    //should rotate each turn
    private var currentStartPlayer: Int = 0
    //player pos expected to do something
    private var currentPosition: Int = 0
    //maybe should change for 3 bools instead
    private var dealerCardsCount: Int = 0

    //bet rules init and blinds calculation
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

    //stores last update per player to prevent spam without cross-player aliasing
    private val lastUpdateBySession: MutableMap<String, Message> = HashMap()
    //game status control
    private val started = AtomicBoolean(false)
    private val gameStarted = AtomicBoolean(false)
    private val dealerTurn = AtomicBoolean(false)
    private val roundEnd = AtomicBoolean(false)

    var deck = CardDeck(roomProperties.deckStacks)

    var dealerHand = CardDeck()

    private fun initialDeal() {
        val players = map.getPlayers()
        takeBlinds()
        if (map.getIsHoldem()) {
            for (player in players) {
                deck.dealCards(2, player.playerDeck)
            }
        }
    }

    private fun takeBlinds() {
        map.getPlayerByPosition(currentStartPlayer).also {
            if (it != null) {
                takeBlind(it, bigBlind)
            }
        }
        map.getPlayerByPosition(currentStartPlayer + 1).also {
            if (it != null) {
                takeBlind(it, smallBlind)
            }
        }
    }

    private fun takeBlind(player: PokerPlayer, amount: Double) {
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
    }

    fun addLatePlayer(userSession: PlayerSession) {
        val player = userSession.player as PokerPlayer
        map.addPlayer(player)
        super.onRoomCreated(listOf(userSession))
        send(
            userSession,
            Message(
                GAME_ROOM_JOIN_SUCCESS,
                GameSettingsPack(gameRoomId.toString(), roomProperties.loopRate)
            )
        )
        log.trace("Late join player {} on room {}", player.id, key())
    }

    override fun onRoomCreated(userSessions: List<PlayerSession>) {
        //assign sessions to players
        if (userSessions.isNotEmpty()) {
            userSessions.forEach {
                val player = it.player as PokerPlayer
                player.position = userSessions.size
                map.addPlayer(player)
            }
        }
        //send join messages
        super.onRoomCreated(userSessions)
        //send settings to client(optional to use, loop rate used only on server)
        sendBroadcast(
            Message(
                GAME_ROOM_JOIN_SUCCESS,
                GameSettingsPack(
                    gameRoomId.toString(),
                    roomProperties.loopRate
                )
            )
        )

        schedulePeriodically(
            this,
            roomProperties.initDelay,
            roomProperties.loopRate
        )

        schedule(
            { roomService.onGameEnd(this) },
            roomProperties.endDelay + roomProperties.startDelay
        )

        log.trace("Room {} has been created", key())
    }

    //start room
    override fun onRoomStarted() {
        started.set(true)
        sendBroadcast(
            Message(
                GAME_ROOM_START,
                RoomPack(
                    ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
                        .plus(roomProperties.startDelay, ChronoUnit.MILLIS).toInstant().toEpochMilli(),
                    gameRoomId.toString()
                )
            )
        )
    }

    override fun onGameStarted() {
        log.info("Room {}. Game has been started", key())
        sendBroadcast(
            Message(
                GAME_START,
                RoomPack(
                    ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
                        .plus(roomProperties.endDelay, ChronoUnit.MILLIS).toInstant().toEpochMilli(),
                    gameRoomId.toString()
                )
            )
        )
    }

    private fun collectUpdate(player: PokerPlayer): Message {
        if (player.isAlive) player.update()
        val updatePack = player.getPrivateUpdatePack()
        val playerUpdatePackList = map.getPlayers()
            .map { mapped ->
                if (mapped.id != player.id) {
                    mapped.getSecretUpdatePack()
                }
                else mapped.getUpdatePack()
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
                lastMaxBet
            )
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

    //in future should be somewhat transactional, don't apply any changes to stake or pot before check
    fun onPlayerDecision(userSession: PlayerSession, event: PokerPlayerDecisionEvent) {
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

        // Texas Hold'em: 5 community cards is the river — next dealer event is showdown.
        if (dealerHand.getCards().size >= 5) {
            triggerShowdown()
            return
        }
        deck.dealCard(dealerHand)
    }

    private fun triggerShowdown() {
        if (roundEnd.get()) return
        val nonFolded = map.getPlayers().filter { !it.folded }
        val canEvaluate = (dealerHand.getCards().size + 2) >= 5
        val contestants = map.getPlayers()
            .filter { it.totalContribution > 0.0 }
            .map { player ->
                val hand = when {
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

    private fun broadcastShowdown(distribution: PokerDistribution, revealHands: Boolean) {
        val entries = map.getPlayers().map { player ->
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
        val pots = distribution.pots.map {
            PokerShowdownSidePot(it.amount, it.eligibleIds, it.winnerIds)
        }
        sendBroadcast(Message(SHOWDOWN_RESULT, PokerShowdownPack(entries, pots)))
    }

    override fun update() {
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
        }
        else {
            for (currentPlayer in map.getPlayers()) {
                send(
                    currentPlayer.userSession,
                    collectUpdate(currentPlayer)
                )
                send(
                    currentPlayer.userSession,
                    Message(
                        GAME_ROOM_STATUS
                    )
                )
            }
            resetTable()
        }
    }
    //main game cycle driver
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
        val currentLastPlayer = if (currentStartPlayer != 0) currentStartPlayer - 1
                                else playersCount - 1

        //last user reached and all bets valid — round complete, dealer turn
        if (currentPosition == currentLastPlayer && allBetsValid()) {
            currentStartPlayer = (currentStartPlayer + 1) % playersCount
            return onDealerTurn()
        }
        //advance to the next active (not folded, not all-in) player
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
    //check if all players placed their bets and everyone chose call, all-in or fold
    fun allBetsValid(): Boolean {
        for (player in map.getPlayers()) {
            if (player.currentBet != lastMaxBet) {
                if (!player.folded && !player.allin) return false
            }
        }
        return true
    }

    fun onBuyIn(userSession: PlayerSession, event: BetEvent) {
        val player = userSession.player as PokerPlayer
        if (player.boughtIn) {
            sendBetFailure(userSession, FailureCode.INVALID_BET, "Already bought in")
            return
        }
        val buyIn = event.bet
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
        player.bet = buyIn
        player.balance -= buyIn
        player.stack = buyIn
        player.boughtIn = true
        userSession.userId?.let { uid ->
            ledgerService?.applyDelta(uid, UUID.randomUUID(), -buyIn, BalanceLedgerReason.POKER_BUY_IN)
                ?.subscribe()
        }
        if (gameStarted.get()) {
            // Late buy-in: seat is funded, but the player sits out the in-progress
            // round. resetTable() unfolds them at round end so they play next hand.
            player.folded = true
            return
        }
        map.getPlayers().forEach {
            if (!it.boughtIn) return
        }
        gameStarted.set(true)
        initialTurn()
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
    }

    override fun onPlayerInfoRequest(userSession: PlayerSession) {
        send(
            userSession, Message(
                INFO,
                InfoPack(
                    (userSession.player as PokerPlayer).getInfoPack(),
                    roomProperties.loopRate,
                    map.alivePlayers()
                )
            )
        )
    }

    override fun onDestroy(userSessions: List<PlayerSession>) {
        userSessions.forEach { userSession: PlayerSession ->
            map.removePlayer(
                userSession.player as PokerPlayer
            )
        }
        super.onDestroy(userSessions)
    }

    override fun onClose(userSession: PlayerSession) {
        send(userSession, Message(GAME_ROOM_CLOSE))
        super.onClose(userSession)
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

    private fun cashOutOnDisconnect(userSession: PlayerSession, player: PokerPlayer) {
        if (!player.boughtIn) return
        val remaining = player.stack
        player.stack = 0.0
        player.boughtIn = false
        if (remaining <= 0.0) return
        player.balance += remaining
        val uid = userSession.userId ?: return
        ledgerService?.applyDelta(uid, UUID.randomUUID(), remaining, BalanceLedgerReason.POKER_CASH_OUT)
            ?.subscribe()
    }
}