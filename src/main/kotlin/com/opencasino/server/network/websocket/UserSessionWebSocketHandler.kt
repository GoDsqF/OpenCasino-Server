package com.opencasino.server.network.websocket

import com.google.gson.Gson
import com.opencasino.server.config.*
import com.opencasino.server.event.BetEvent
import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.event.BlackjackPlayerDecisionEvent
import com.opencasino.server.event.poker.PokerPlayerDecisionEvent
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
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
    private val roomService: RoomService,
) {
    private val objectMapper = Gson()

    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
    }

    fun onNext(message: Message) {
        //val messageData = if (message.data != null) message.data as Map<*, *> else null
        val messageData = if (message.data != null) message.data as Map<*, *> else null

        when(message.serviceId) {
            AvailableGames.Blackjack.name -> {
                when (message.type) {
                    GAME_ROOM_JOIN -> {
                        log.debug("Join attempt from {} to Blackjack", userSession.handshakeInfo.remoteAddress)
                        roomService.addPlayerToWait(
                            userSession,
                            objectMapper.fromJson(messageData.toString(), GameRoomJoinEvent::class.java)
                        )
                    }

                    INFO -> {
                        roomService.getRoomByKey(userSession.roomKey)
                            .ifPresent { it.onPlayerInfoRequest(userSession) }
                    }

                    PLAYER_DECISION -> {
                        roomService.getRoomByKey(userSession.roomKey)
                            .ifPresent {
                                it as BlackjackGameRoom
                                it.onPlayerDecision(
                                    userSession,
                                    objectMapper.fromJson(messageData.toString(), BlackjackPlayerDecisionEvent::class.java)
                                )
                            }
                    }

                    BET -> {
                        roomService.getRoomByKey(userSession.roomKey)
                            .ifPresent {
                                it as BlackjackGameRoom
                                it.onBet(
                                    userSession,
                                    objectMapper.fromJson(messageData.toString(), BetEvent::class.java)
                                )
                            }
                    }

                }
            }

            AvailableGames.PockerHoldEm.name -> {
                when (message.type) {
                    GAME_ROOM_JOIN -> {
                        log.debug("Join attempt from {} to HoldEm Pocker", userSession.handshakeInfo.remoteAddress)
                        roomService.addPlayerToWait(
                            userSession,
                            objectMapper.fromJson(messageData.toString(), GameRoomJoinEvent::class.java)
                        )
                    }

                    INFO -> {
                        roomService.getRoomByKey(userSession.roomKey)
                            .ifPresent { it.onPlayerInfoRequest(userSession) }
                    }

                    PLAYER_DECISION -> {
                        roomService.getRoomByKey(userSession.roomKey)
                            .ifPresent {
                                it as PokerGameRoom
                                it.onPlayerDecision(
                                    userSession,
                                    objectMapper.fromJson(messageData.toString(), PokerPlayerDecisionEvent::class.java)
                                )
                            }
                    }

                    BET -> {
                        roomService.getRoomByKey(userSession.roomKey)
                            .ifPresent {
                                it as PokerGameRoom
                                it.onBet(
                                    userSession,
                                    objectMapper.fromJson(messageData.toString(), BetEvent::class.java)
                                )
                            }
                    }
                }
            }

            AvailableGames.PockerOmaha.name -> {
                when (message.type) {
                    GAME_ROOM_JOIN -> {
                        log.debug("Join attempt from {} to Omaha Pocker", userSession.handshakeInfo.remoteAddress)
                        roomService.addPlayerToWait(
                            userSession,
                            objectMapper.fromJson(messageData.toString(), GameRoomJoinEvent::class.java)
                        )
                    }

                    INFO -> {
                        roomService.getRoomByKey(userSession.roomKey)
                            .ifPresent { it.onPlayerInfoRequest(userSession) }
                    }

                    PLAYER_DECISION -> {
                        roomService.getRoomByKey(userSession.roomKey)
                            .ifPresent {
                                it as PokerGameRoom
                                it.onPlayerDecision(
                                    userSession,
                                    objectMapper.fromJson(messageData.toString(), PokerPlayerDecisionEvent::class.java)
                                )
                            }
                    }

                    BET -> {
                        roomService.getRoomByKey(userSession.roomKey)
                            .ifPresent {
                                it as PokerGameRoom
                                it.onBet(
                                    userSession,
                                    objectMapper.fromJson(messageData.toString(), BetEvent::class.java)
                                )
                            }
                    }
                }
            }
        }


    }
}