package com.opencasino.server.network.pack.blackjack.websocket

import com.opencasino.server.network.pack.blackjack.shared.BlackjackUserSession
import com.opencasino.server.service.shared.MessageType
import java.util.function.Function

interface BlackjackWebSocketMessagePublisher {
    fun send(userSession: BlackjackUserSession, message: Any)
    fun sendFailure(userSession: BlackjackUserSession, message: Any)
    fun sendBroadcast(type: MessageType, message: String)
    fun sendBroadcast(message: Any)
    fun sendBroadcast(userSessions: Collection<BlackjackUserSession>, message: Any)
    fun send(userSession: BlackjackUserSession, function: Function<BlackjackUserSession, Any>)
    fun sendBroadcast(messageFunction: Function<BlackjackUserSession, Any>)
    fun sendBroadcast(
        userSessions: Collection<BlackjackUserSession>,
        function: Function<BlackjackUserSession, Any>
    )
}