package com.opencasino.server.network.websocket


import com.opencasino.server.network.pack.blackjack.shared.BlackjackPlayerSession
import com.opencasino.server.service.shared.MessageType
import java.util.function.Function

interface WebSocketMessagePublisher {
    fun send(userSession: BlackjackPlayerSession, message: Any)
    fun sendFailure(userSession: BlackjackPlayerSession, message: Any)
    fun sendBroadcast(type: MessageType, message: String)
    fun sendBroadcast(message: Any)
    fun sendBroadcast(userSessions: Collection<BlackjackPlayerSession>, message: Any)
    fun send(userSession: BlackjackPlayerSession, function: Function<BlackjackPlayerSession, Any>)
    fun sendBroadcast(messageFunction: Function<BlackjackPlayerSession, Any>)
    fun sendBroadcast(userSessions: Collection<BlackjackPlayerSession>, function: Function<BlackjackPlayerSession, Any>)
}