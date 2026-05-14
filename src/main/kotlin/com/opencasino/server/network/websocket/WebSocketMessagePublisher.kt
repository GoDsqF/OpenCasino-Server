package com.opencasino.server.network.websocket


import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.shared.FailureCode
import com.opencasino.server.service.shared.MessageType
import java.util.function.Function

interface WebSocketMessagePublisher {
    fun send(userSession: PlayerSession, message: Any)
    fun sendFailure(userSession: PlayerSession, code: FailureCode, message: String, details: Any? = null)
    fun sendBetFailure(userSession: PlayerSession, code: FailureCode, message: String, details: Any? = null)
    fun sendJoinFailure(userSession: PlayerSession, code: FailureCode, message: String, details: Any? = null)
    fun sendBroadcast(type: MessageType, message: String)
    fun sendBroadcast(message: Any)
    fun sendBroadcast(userSessions: Collection<PlayerSession>, message: Any)
    fun send(userSession: PlayerSession, function: Function<PlayerSession, Any>)
    fun sendBroadcast(messageFunction: Function<PlayerSession, Any>)
    fun sendBroadcast(userSessions: Collection<PlayerSession>, function: Function<PlayerSession, Any>)
}