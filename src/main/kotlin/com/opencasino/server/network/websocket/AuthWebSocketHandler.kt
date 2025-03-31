package com.opencasino.server.network.websocket


import com.google.gson.Gson
import com.opencasino.server.service.auth.AuthService
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.*
import reactor.core.publisher.Mono

@Component
class AuthWebSocketHandler(
    private val authService: AuthService
) : WebSocketHandler {
    override fun handle(session: WebSocketSession): Mono<Void> {
        return session.receive()
            .map { it.payloadAsText }
            .map { authCode ->
                val authEvent = authService.authenticate(authCode)
                val responseGson = Gson().toJson(authEvent)
                session.textMessage(responseGson)
            }
            .flatMap { session.send(Mono.just(it)) }
            .then()
    }
}