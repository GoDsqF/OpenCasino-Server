package com.opencasino.server.game.poker.holdem.room

import com.opencasino.server.config.*
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.poker.PokerPlayerDecisionEvent
import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.poker.holdem.map.PokerMap
import com.opencasino.server.game.poker.holdem.model.PokerCondition
import com.opencasino.server.game.poker.holdem.model.PokerHand
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.network.pack.poker.info.InfoPack
import com.opencasino.server.network.pack.poker.shared.PokerConditionPack
import com.opencasino.server.network.pack.poker.shared.RoomPack
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.pack.poker.shared.GameSettingsPack
import com.opencasino.server.network.pack.poker.update.GameUpdatePack
import com.opencasino.server.network.pack.shared.DealerUpdatePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.impl.PokerRoomServiceImpl
import com.opencasino.server.service.shared.PokerDecision
import reactor.core.scheduler.Scheduler
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class PokerGameRoom(
    val map: PokerMap,
    gameRoomId: UUID,
    roomService: PokerRoomServiceImpl,
    webSocketSessionService: WebSocketSessionService,
    schedulerService: Scheduler,
    val gameProperties: GameProperties,
    val roomProperties: PokerRoomProperties
) : AbstractPokerGameRoom(gameRoomId, schedulerService, roomService, webSocketSessionService) {

    var pot: Int = 0

    private val testing = AtomicBoolean(false)

    private var currentStartPlayer: Int = 0
    private var currentMaxBet: Int = 0
    private var currentMinRaise: Int = 0
    private var lastBetSize: Int = 0
    
    private lateinit var lastUpdate: Message
    private val started = AtomicBoolean(false)
    private val gameStarted = AtomicBoolean(false)

    private var condition: PokerCondition? = null

    //deck initialize
    var deck = CardDeck(8)

    //dealer hand
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
        var player = map.getPlayerByPosition(0 + currentStartPlayer )
        if (player?.stack!! >= roomProperties.smallBlind) {

        }
        map.getPlayerByPosition(0 + currentStartPlayer )?.stack?.minus(roomProperties.smallBlind)
        map.getPlayerByPosition(1 + currentStartPlayer )?.stack?.minus(roomProperties.smallBlind)
    }

    private fun initialTurn() {
        var result: PokerCondition? = null

    }

    override fun onRoomCreated(userSessions: List<PlayerSession>) {
        if (userSessions.isNotEmpty()) {
            userSessions.forEach {
                val player = it.player as PokerPlayer
                player.position = userSessions.size
                map.addPlayer(player)
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

    private fun collectUpdate(player: PokerPlayer): Message {
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
                val newUpdate = collectUpdate(currentPlayer)
                if (this::lastUpdate.isInitialized) {
                    if (lastUpdate.data != newUpdate.data) {
                        send(
                            currentPlayer.userSession,
                            newUpdate
                        )
                        lastUpdate = newUpdate
                    }
                }
                else {
                    lastUpdate = newUpdate
                    send(
                        currentPlayer.userSession,
                        newUpdate
                    )
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
                        PokerConditionPack(
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
                    (userSession.player as PokerPlayer).getInfoPack(),
                    roomProperties.loopRate,
                    map.alivePlayers()
                )
            )
        )
    }

    fun onDealerTurn(): PokerCondition? {
        var result: PokerCondition? = null

        deck.dealCard(dealerHand)


    }

    private fun calculateHand(hand: CardDeck): Int {
        val cards = hand.getCards()
        var result = PokerHand.fromList(hand.getCards()).
    }

    fun onPlayerDecision(userSession: PlayerSession, event: PokerPlayerDecisionEvent) {
        if (!started.get()) return
        val player = userSession.player as PokerPlayer
        if (!player.isAlive) return
        val decision = PokerDecision.valueOf(event.inputId)
        val amount = event.amount
        player.updateState(decision, amount)
    }

    fun Int?.isValidBet(lastBetSize: Int, betType: PokerDecision): Boolean {
        if (betType == )
        when {
            this == null -> return false
            this < 0 -> return false
            this < (currentMaxBet ?: 0) + lastBetSize -> return false
            this < ()
        }

    }

    fun onCheck(userSession: PlayerSession) {
        if (!started.get()) return
        val player = userSession.player as PokerPlayer
        if (!player.isAlive) return
        if (currentStartPlayer != map.getPlayers().size - 1) {
            currentStartPlayer++
        }
        else {
            currentStartPlayer = 0
            onDealerTurn()
        }
    }

    fun onCall(userSession: PlayerSession, amount: Int) {
        if (!started.get()) return
        val player = userSession.player as PokerPlayer
        if (!player.isAlive) return
    }

    fun onRaise(userSession: PlayerSession, amount: Int) {
        if (!started.get()) return
        val player = userSession.player as PokerPlayer
        if (!player.isAlive) return
        val decision = PokerDecision.RAISE
    }

    fun onFold(userSession: PlayerSession) {
        if (!started.get()) return
        val player = userSession.player as PokerPlayer
        if (!player.isAlive) return
        val decision = PokerDecision.FOLD
    }

    fun onBet(userSession: PlayerSession, event: BetEvent) {
        if (gameStarted.get()) return
        val player = userSession.player as PokerPlayer
        player.bet = event.bet
        player.balance -= event.bet
        if (testing.get()) return
        else initialDeal()
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
                userSession.player as PokerPlayer
            )
        }
        super.onDestroy(userSessions)
    }

    override fun onClose(userSession: PlayerSession) {
        send(userSession, Message(GAME_ROOM_CLOSE))
        super.onClose(userSession)
    }
}