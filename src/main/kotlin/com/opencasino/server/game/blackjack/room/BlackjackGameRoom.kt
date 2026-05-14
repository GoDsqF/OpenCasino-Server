package com.opencasino.server.game.blackjack.room

import com.opencasino.server.config.*
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.BlackjackPlayerDecisionEvent
import com.opencasino.server.game.blackjack.map.BlackjackMap
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.blackjack.model.BlackjackCondition
import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.model.Rank
import com.opencasino.server.network.pack.blackjack.info.InfoPack
import com.opencasino.server.network.pack.blackjack.shared.BlackjackConditionPack
import com.opencasino.server.network.pack.blackjack.shared.RoomPack
import com.opencasino.server.network.pack.shared.DealerUpdatePack
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.pack.blackjack.shared.GameSettingsPack
import com.opencasino.server.network.pack.blackjack.update.GameUpdatePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.impl.BlackjackRoomServiceImpl
import com.opencasino.server.service.shared.BlackjackDecision
import com.opencasino.server.service.shared.FailureCode
import reactor.core.scheduler.Scheduler
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class BlackjackGameRoom(
    val map: BlackjackMap,
    gameRoomId: UUID,
    roomService: BlackjackRoomServiceImpl,
    webSocketSessionService: WebSocketSessionService,
    schedulerService: Scheduler,
    val gameProperties: GameProperties,
    val roomProperties: BlackjackRoomProperties
) : AbstractBlackjackGameRoom(gameRoomId, schedulerService, roomService, webSocketSessionService) {
    private val testing = AtomicBoolean(false)
    
    private val lastUpdateBySession: MutableMap<String, Message> = HashMap()
    private val started = AtomicBoolean(false)
    private val gameStarted = AtomicBoolean(false)

    private var condition: BlackjackCondition? = null

    private val combiner: Map<Rank, Int> = mapOf(
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
        Rank.CA to 11
    )

    //deck initialize
    var deck = CardDeck(roomProperties.deckStacks)

    //initialize map of player hands


    //dealer hand
    var dealerHand = CardDeck()

    private fun initialDeal() {
        val players = map.getPlayers()
        for (player in players) {
            deck.dealCards(2, player.playerDeck)
        }
        deck.dealCard(dealerHand, true)
        deck.dealCard(dealerHand, false)
        gameStarted.set(true)
        initialCheck()
    }

    private fun initialCheck(): BlackjackCondition? {
        var result: BlackjackCondition? = null
        val dealerSum = calculateScore(dealerHand)
        val playerSum = calculateScore(map.getPlayers().first().playerDeck)

        if (dealerSum == 21) {
            if (playerSum != 21) result = BlackjackCondition.DealerBlackjack
        }
        else if (playerSum == 21) result = BlackjackCondition.PlayerWinBlackjack

        condition = result
        return result
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

    private fun collectUpdate(player: BlackjackPlayer): Message {
        if (player.isAlive) player.update()
        val updatePack = player.getPrivateUpdatePack()
        val playerUpdatePackList = map.getPlayers()
            .map { it.getUpdatePack() }

        val dealerUpdatePack = DealerUpdatePack(dealerHand.toPublicView())

        return Message(
            UPDATE,
            GameUpdatePack(
                updatePack,
                playerUpdatePackList,
                dealerUpdatePack
            )
        )
    }

    override fun update() {
        if (!gameStarted.get()) return
        if (condition == null) {
            for (currentPlayer in map.getPlayers()) {
                /*if (currentPlayer.isAlive) currentPlayer.update()
                val updatePack = currentPlayer.getPrivateUpdatePack()
                val playerUpdatePackList = map.getPlayers()
                    .map { it.getUpdatePack() }

                val dealerCards = mutableListOf<Card?>()
                dealerHand.getCards()
                    .forEach {
                        if (it.visible) {
                            dealerCards.add(it)
                        }
                        else {
                            dealerCards.add(null)
                        }
                    }
                val dealerUpdatePack = DealerUpdatePack(dealerCards)*/
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
            val dealerCards = dealerHand.getCards()
            dealerCards.forEach {
                it.visible = true
            }
            for (currentPlayer in map.getPlayers()) {
                send(
                    currentPlayer.userSession,
                    collectUpdate(currentPlayer)
                )
                send(
                    currentPlayer.userSession,
                    Message(
                        GAME_ROOM_STATUS,
                        BlackjackConditionPack(
                            condition.toString()
                        )
                    )
                )
            }
            reset()
        }
    }

    override fun onPlayerInfoRequest(userSession: PlayerSession) {
        send(
            userSession, Message(
                INFO,
                InfoPack(
                    (userSession.player as BlackjackPlayer).getInfoPack(),
                    roomProperties.loopRate,
                    map.alivePlayers()
                )
            )
        )
    }

    fun onPlayerTurn(): Pair<BlackjackCondition?, Int> {
        var result: Pair<BlackjackCondition?, Int> = Pair(null, 0)
        val playerSum = calculateScore(map.getPlayers().first().playerDeck)

        if (playerSum > 21) {
            result = Pair(BlackjackCondition.DealerWin, playerSum)
        } else if (playerSum == 21) onDealerTurn()

        condition = result.first
        return result
    }

    fun onDealerTurn(): BlackjackCondition? {
        dealerHand.openCards()
        val playerSum = calculateScore(map.getPlayers().first().playerDeck)

        var dealerSum = calculateScore(dealerHand)
        while (dealerSum < 17) {
            deck.dealCard(dealerHand)
            dealerSum = calculateScore(dealerHand)
        }

        val result: BlackjackCondition = when {
            dealerSum > 21 -> BlackjackCondition.PlayerWin
            dealerSum < playerSum -> BlackjackCondition.PlayerWin
            dealerSum > playerSum -> BlackjackCondition.DealerWin
            else -> BlackjackCondition.Draw
        }

        condition = result
        return result
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

    override fun onPlayerDecision(userSession: PlayerSession, event: BlackjackPlayerDecisionEvent) {
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

    override fun onBet(userSession: PlayerSession, event: BetEvent) {
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

    /*private fun onTestingDeal() {
        val players = map.getPlayers()
        for (player in players) {
            player.playerDeck.addCard(
                Card(
                    Rank.CA,
                    Suit.Hearts
                )
            )
            player.playerDeck.addCard(
                Card(
                    Rank.CA,
                    Suit.Clubs
                )
            )
        }
        deck.dealCard(dealerHand, true)
        deck.dealCard(dealerHand, false)
        gameStarted.set(true)
        initialCheck()
    }*/

    private fun reset() {
        gameStarted.set(false)
        condition = null
        map.getPlayers().forEach {
            it.playerDeck.clear()
            it.bet = 0.00
        }
        dealerHand = CardDeck()
        if (deck.getCards().size < roomProperties.reshuffleThreshold) {
            deck = CardDeck(roomProperties.deckStacks)
        }
        onGameStarted()
    }

    override fun onDestroy(userSessions: List<PlayerSession>) {
        userSessions.forEach { userSession: PlayerSession ->
            map.removePlayer(
                userSession.player as BlackjackPlayer
            )
        }
        super.onDestroy(userSessions)
    }

    override fun onClose(userSession: PlayerSession) {
        send(userSession, Message(GAME_ROOM_CLOSE))
        super.onClose(userSession)
    }
}