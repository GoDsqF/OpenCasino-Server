package com.opencasino.server.game.blackjack.room

import com.opencasino.server.config.*
import com.opencasino.server.event.PlayerDecisionEvent
import com.opencasino.server.game.blackjack.map.BlackjackMap
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.network.pack.blackjack.init.BlackjackInitPack
import com.opencasino.server.network.pack.blackjack.shared.BlackjackRoomPack
import com.opencasino.server.network.pack.blackjack.shared.BlackjackPlayerSession
import com.opencasino.server.network.pack.blackjack.shared.GameSettingsPack
import com.opencasino.server.network.pack.blackjack.update.GameUpdatePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.blackjack.BlackjackRoomService
import com.opencasino.server.service.blackjack.BlackjackWebSocketSessionService
import com.opencasino.server.service.blackjack.shared.BlackjackDecision
import reactor.core.scheduler.Scheduler
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class BlackjackGameRoom(
    val map: BlackjackMap,
    gameRoomId: UUID,
    roomService: BlackjackRoomService,
    webSocketSessionService: BlackjackWebSocketSessionService,
    schedulerService: Scheduler,
    val gameProperties: GameProperties,
    val roomProperties: RoomProperties
) : AbstractBlackjackGameRoom(gameRoomId, schedulerService, roomService, webSocketSessionService) {
    private val started = AtomicBoolean(false)

    private val deck = CardDeck(8)

    val playersDecks = mutableMapOf<UUID, List<Card>>()

    val dealerDeck = mutableListOf<Card>()

    override fun onRoomCreated(userSessions: List<BlackjackPlayerSession>) {
        if (userSessions.isNotEmpty()) {
            userSessions.forEach {
                val player = it.player as BlackjackPlayer
                player.position = userSessions.size
                map.addPlayer(player)
            }
        }

        super.onRoomCreated(userSessions)

        sendBroadcast {
            Message(
                GAME_ROOM_JOIN_SUCCESS,
                GameSettingsPack(
                    roomProperties.loopRate
                )
            )
        }

        schedulePeriodically(
            this,
            roomProperties.initDelay,
            roomProperties.loopRate
        )

        schedule(
            { roomService.onRoundEnd(this) },
            roomProperties.endDelay + roomProperties.startDelay
        )
    }

    override fun onRoomStarted() {
        started.set(false)
        sendBroadcast {
            Message(
                GAME_ROOM_START,
                BlackjackRoomPack(
                    ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
                        .plus(roomProperties.startDelay, ChronoUnit.MILLIS).toInstant().toEpochMilli()
                )
            )
        }
    }

    override fun onGameStarted() {
        log.trace("Room {}. Game has been started", key())
        started.set(true)
        sendBroadcast {
            Message(
                GAME_ROOM_GAME_START,
                BlackjackRoomPack(
                    ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
                        .plus(roomProperties.endDelay, ChronoUnit.MILLIS).toInstant().toEpochMilli()
                )
            )
        }
    }

    override fun update() {
        if (!started.get()) return
        for (currentPlayer in map.getPlayers()) {
            if (currentPlayer.isAlive) currentPlayer.update()
            val updatePack = currentPlayer.getPrivateUpdatePack()
            val playerUpdatePackList = map.getPlayers()
                .map { it.getUpdatePack() }

            send(
                currentPlayer.userSession,
                Message(
                    UPDATE,
                    GameUpdatePack(
                        updatePack,
                        playerUpdatePackList
                    )
                )
            )
        }
    }

    fun onPlayerInitRequest(userSession: BlackjackPlayerSession) {
        send(
            userSession, Message(
                INIT,
                BlackjackInitPack(
                    (userSession.player as BlackjackPlayer).getInitPack(),
                    roomProperties.loopRate,
                    map.alivePlayers()
                )
            )
        )
    }

    fun onPlayerDecision(userSession: BlackjackPlayerSession, event: PlayerDecisionEvent) {
        if (!started.get()) return
        val player = userSession.player as BlackjackPlayer
        if (!player.isAlive) return
        val decision = BlackjackDecision.valueOf(event.inputId)
        player.updateState(decision, event.state)
    }

    override fun onDestroy(userSessions: List<BlackjackPlayerSession>) {
        userSessions.forEach { userSession: BlackjackPlayerSession ->
            map.removePlayer(
                userSession.player as BlackjackPlayer
            )
        }
        super.onDestroy(userSessions)
    }

    override fun onClose(userSession: BlackjackPlayerSession) {
        send(userSession, Message(GAME_ROOM_CLOSE))
        super.onClose(userSession)
    }
}