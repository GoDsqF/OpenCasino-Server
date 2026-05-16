package com.opencasino.server.network.websocket

import com.google.gson.Gson
import com.opencasino.server.config.*
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.event.BlackjackPlayerDecisionEvent
import com.opencasino.server.event.poker.GameRoomCreateEvent
import com.opencasino.server.event.poker.PokerPlayerDecisionEvent
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.game.poker.holdem.room.PokerGameRoom
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.RoomService
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class UserSessionWebSocketHandler(
    private val userSession: PlayerSession,
    private val webSocketSessionService: WebSocketSessionService,
    private val blackjackRoomService: RoomService,
    private val pokerRoomService: RoomService
) {
    private val objectMapper = Gson()

    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
    }

    fun onNext(message: Message) {
        if (userSession.userId == null) {
            log.warn("Dropping {} from unauthenticated session {}", message.type, userSession.id)
            return
        }
        val messageData = if (message.data != null) message.data as Map<*, *> else null

        when(message.serviceId) {
            AvailableGames.Blackjack.name -> {
                when (message.type) {
                    GAME_ROOM_JOIN -> {
                        log.debug("Join attempt from {} to Blackjack", userSession.handshakeInfo.remoteAddress)
                        blackjackRoomService.addPlayerToWait(
                            userSession,
                            objectMapper.fromJson(messageData.toString(), GameRoomJoinEvent::class.java)
                        )
                    }

                    INFO -> {
                        val room = blackjackRoomService.getRoomByKey(userSession.roomKey) as BlackjackGameRoom
                        room.onPlayerInfoRequest(userSession)
                    }

                    PLAYER_DECISION -> {
                        val room = blackjackRoomService.getRoomByKey(userSession.roomKey) as BlackjackGameRoom
                        room.onPlayerDecision(
                            userSession,
                            objectMapper.fromJson(messageData.toString(), BlackjackPlayerDecisionEvent::class.java)
                        )
                    }

                    BET -> {
                        val room = blackjackRoomService.getRoomByKey(userSession.roomKey) as BlackjackGameRoom
                        room.onBet(
                            userSession,
                            objectMapper.fromJson(messageData.toString(), BetEvent::class.java)
                        )
                    }
                }
            }

            AvailableGames.Poker.name -> {
                when (message.type) {
                    GAME_ROOM_CREATE -> {
                        log.debug("HoldEm Poker room create attempt from {}",
                            userSession.handshakeInfo.remoteAddress)
                        pokerRoomService.addPlayerToWait(
                            userSession,
                            objectMapper.fromJson(messageData.toString(), GameRoomCreateEvent::class.java)
                        )
                    }

                    GAME_ROOM_JOIN -> {
                        log.debug("Join attempt from {} to HoldEm Poker",
                            userSession.handshakeInfo.remoteAddress)
                        pokerRoomService.addPlayerToWait(
                            userSession,
                            objectMapper.fromJson(messageData.toString(), GameRoomJoinEvent::class.java)
                        )
                    }
                    INFO -> {
                        val room = pokerRoomService.getRoomByKey(userSession.roomKey) as PokerGameRoom
                        room.onPlayerInfoRequest(userSession)
                    }

                    PLAYER_DECISION -> {
                        val room = pokerRoomService.getRoomByKey(userSession.roomKey) as PokerGameRoom
                        room.onPlayerDecision(
                            userSession,
                            objectMapper.fromJson(messageData.toString(), PokerPlayerDecisionEvent::class.java)
                        )
                    }

                    BET -> {
                        val room = pokerRoomService.getRoomByKey(userSession.roomKey) as PokerGameRoom
                        room.onBuyIn(
                            userSession,
                            objectMapper.fromJson(messageData.toString(), BetEvent::class.java)
                        )
                    }
                }
            }
        }
    }
}