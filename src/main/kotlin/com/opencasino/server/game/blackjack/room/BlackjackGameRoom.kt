package com.opencasino.server.game.blackjack.room

import com.opencasino.server.config.*
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.PlayerDecisionEvent
import com.opencasino.server.game.blackjack.map.BlackjackMap
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.blackjack.model.BlackjackCondition
import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.Card.Rank
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.network.pack.blackjack.info.BlackjackInfoPack
import com.opencasino.server.network.pack.blackjack.shared.BlackjackConditionPack
import com.opencasino.server.network.pack.blackjack.shared.BlackjackRoomPack
import com.opencasino.server.network.pack.blackjack.shared.DealerUpdatePack
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.pack.blackjack.shared.GameSettingsPack
import com.opencasino.server.network.pack.update.GameUpdatePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.BlackjackDecision
import reactor.core.scheduler.Scheduler
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class BlackjackGameRoom(
    val map: BlackjackMap,
    gameRoomId: UUID,
    roomService: RoomService,
    webSocketSessionService: WebSocketSessionService,
    schedulerService: Scheduler,
    val gameProperties: GameProperties,
    val roomProperties: BlackjackRoomProperties
) : AbstractBlackjackGameRoom(gameRoomId, schedulerService, roomService, webSocketSessionService) {
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
        Rank.Jack to 10,
        Rank.Queen to 10,
        Rank.King to 10,
        Rank.Ace to 11
    )

    //deck initialize
    var deck = CardDeck(8)

    //initialize map of player hands
    var playersHands = mutableMapOf<Long, CardDeck>()


    //dealer hand
    var dealerHand = CardDeck()

    private fun initialDeal() {
        val players = map.getPlayers()
        for (player in players) {
            deck.dealCards(2, player.playerDeck)
        }
        deck.dealCard(dealerHand, true)
        deck.dealCard(dealerHand, false)
        initialCheck()
    }

    private fun initialCheck(): BlackjackCondition? {
        var result: BlackjackCondition? = null
        val dealerSum = calculateScore(dealerHand)
        val playerSum = calculateScore(map.getPlayers().first().playerDeck)

        if (dealerSum == playerSum) result = BlackjackCondition.Draw
        else if (dealerSum == 21) result = BlackjackCondition.DealerBlackjack
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

                playersHands[player.id] = CardDeck()
            }
        }

        super.onRoomCreated(userSessions)

        sendBroadcast(
            Message(
                GAME_ROOM_JOIN_SUCCESS,
                GameSettingsPack(
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
            { roomService.onRoundEnd(this) },
            roomProperties.endDelay + roomProperties.startDelay
        )

        log.trace("Room {} has been created", key())
    }

    override fun onRoomStarted() {
        started.set(true)
        sendBroadcast(
            Message(
                GAME_ROOM_START,
                BlackjackRoomPack(
                    ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
                        .plus(roomProperties.startDelay, ChronoUnit.MILLIS).toInstant().toEpochMilli()
                )
            )
        )
    }

    override fun onGameStarted() {
        log.info("Room {}. Game has been started", key())
        gameStarted.set(true)
        sendBroadcast(
            Message(
                GAME_START,
                BlackjackRoomPack(
                    ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
                        .plus(roomProperties.endDelay, ChronoUnit.MILLIS).toInstant().toEpochMilli()
                )
            )
        )
    }

    private fun collectUpdate(player: BlackjackPlayer): Message {
        if (player.isAlive) player.update()
        val updatePack = player.getPrivateUpdatePack()
        val playerUpdatePackList = map.getPlayers()
            .map { it.getUpdatePack() }

        val dealerCards = mutableListOf<Card?>()
        dealerHand.getCards()
            .forEach {
                if (it.visible) {
                    dealerCards.add(it)
                } else {
                    dealerCards.add(null)
                }
            }
        val dealerUpdatePack = DealerUpdatePack(dealerCards)

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

                send(
                    currentPlayer.userSession,
                    collectUpdate(currentPlayer)
                )
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

    fun onPlayerInfoRequest(userSession: PlayerSession) {
        send(
            userSession, Message(
                INFO,
                BlackjackInfoPack(
                    (userSession.player as BlackjackPlayer).getInfoPack(),
                    roomProperties.loopRate,
                    map.alivePlayers()
                )
            )
        )
    }

    fun onPlayerTurn(): Pair<BlackjackCondition?, Int> {
        var result: Pair<BlackjackCondition?, Int> = Pair(null, 0)
        var playerSum = calculateScore(map.getPlayers().first().playerDeck)

        if (playerSum > 21) {
            result = Pair(BlackjackCondition.DealerWin, playerSum)
        }

        condition = result.first
        return result
    }

    fun onDealerTurn(): BlackjackCondition? {
        var result: BlackjackCondition? = null
        dealerHand.openCards()
        val dealerSum = calculateScore(dealerHand)
        val playerSum = calculateScore(map.getPlayers().first().playerDeck)

        if (dealerSum < 17) {
            deck.dealCard(dealerHand)
            onDealerTurn()
            return null
        } else if (dealerSum > 21) {
            result = BlackjackCondition.PlayerWin
        } else if (dealerSum < playerSum) {
            result = BlackjackCondition.PlayerWin
        } else if (dealerSum > playerSum) {
            result = BlackjackCondition.DealerWin
        }

        condition = result
        return result
    }

    private fun calculateScore(hand: CardDeck): Int {
        val cards = hand.getCards()
        var score = cards.sumOf { combiner[it.rank]!! }
        var numAces = cards.count { it.rank == Rank.Ace }

        while (score > 21 && numAces > 0) {
            score -= 10
            numAces--
        }

        return score
    }

    fun onPlayerDecision(userSession: PlayerSession, event: PlayerDecisionEvent) {
        if (!started.get()) return
        val player = userSession.player as BlackjackPlayer
        if (!player.isAlive) return
        val decision = BlackjackDecision.valueOf(event.inputId)
        player.updateState(decision)
    }

    fun onBet(userSession: PlayerSession, event: BetEvent) {
        val player = userSession.player as BlackjackPlayer
        player.bet = event.bet
        player.balance -= event.bet
        initialDeal()
    }

    private fun reset() {
        gameStarted.set(false)
        condition = null
        map.getPlayers().forEach {
            it.playerDeck.clear()
            it.bet = 0.00
        }
        dealerHand = CardDeck()
        if (deck.getCards().size < 64) deck = CardDeck(8)
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