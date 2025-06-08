package com.opencasino.server.network.websocket

import com.google.gson.Gson
import com.opencasino.server.config.GAME_ROOM_JOIN
import com.opencasino.server.config.INIT
import com.opencasino.server.config.PLAYER_DECISION
import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.event.PlayerDecisionEvent
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.blackjack.BlackjackRoomService
import com.opencasino.server.service.blackjack.BlackjackWebSocketSessionService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class UserSessionWebSocketHandler(
    private val userSession: PlayerSession,
    private val webSocketSessionService: WebSocketSessionService,
    private val roomService: BlackjackRoomService,
) {
    private val objectMapper = Gson()

    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
    }

    fun onNext(message: Message) {
        val messageData = if (message.data != null) message.data as Map<*, *> else null

        when (message.type) {
            GAME_ROOM_JOIN -> {
                log.debug("Join attempt from {}", userSession.handshakeInfo.remoteAddress)
                roomService.addPlayerToWait(
                    userSession,
                    objectMapper.fromJson(messageData.toString(), GameRoomJoinEvent::class.java)
                )
            }

            INIT -> {
                roomService.getRoomByKey(userSession.roomKey)
                    .ifPresent { it.onPlayerInitRequest(userSession) }
            }

            PLAYER_DECISION -> {
                roomService.getRoomByKey(userSession.roomKey)
                    .ifPresent {
                        it.onPlayerDecision(
                            userSession,
                            objectMapper.fromJson(messageData.toString(), PlayerDecisionEvent::class.java)
                        )
                    }
            }

        }
    }
}