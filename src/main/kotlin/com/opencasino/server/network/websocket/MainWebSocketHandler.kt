package com.opencasino.server.network.websocket

import com.google.gson.Gson
import com.opencasino.server.game.model.Player
import com.opencasino.server.network.pack.blackjack.shared.BlackjackUserSession
import com.opencasino.server.network.pack.blackjack.websocket.BlackjackUserSessionWebSocketHandler
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.UserSession
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.blackjack.BlackjackRoomService
import com.opencasino.server.service.blackjack.BlackjackWebSocketSessionService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.InputStreamReader

@Service
class MainWebSocketHandler(
    private val webSocketSessionService: BlackjackWebSocketSessionService
) : WebSocketHandler {

    private val objectMapper = Gson()
    private lateinit var roomService: BlackjackRoomService

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun handle(webSocketSession: WebSocketSession): Mono<Void> {
        val input = webSocketSession.receive().share()
        val userSession = BlackjackUserSession(webSocketSession.id, webSocketSession.handshakeInfo)
        val sessionHandler = BlackjackUserSessionWebSocketHandler(
            userSession,
            webSocketSessionService, roomService
        )

        val receive = input
            .filter { it.type == WebSocketMessage.Type.TEXT }
            .map(this::toMessage)
            .doOnNext(sessionHandler::onNext)

        val send = webSocketSession.send(webSocketSessionService.onActive(userSession)
            .map {
                webSocketSession.textMessage(objectMapper.toJson(it))
            }
            .doOnError { handleError(webSocketSession, it) })

        val security = webSocketSession.handshakeInfo.principal.doOnNext {
            webSocketSessionService.onPrincipalInit(userSession, it)
        }
        return Flux.merge(receive, send, security)
            .doOnSubscribe { webSocketSessionService.onSubscribe(userSession, it) }
            .doOnTerminate { webSocketSessionService.onInactive(userSession) }
            .doOnError { handleError(webSocketSession, it) }
            .then()
    }

    private fun toMessage(webSocketMessage: WebSocketMessage): Message =
        objectMapper.fromJson(InputStreamReader(webSocketMessage.payload.asInputStream()), Message::class.java)

    private fun handleError(webSocketSession: WebSocketSession, exception: Throwable) {
        log.error("Error in ${webSocketSession.id} session", exception)
    }

    @Autowired
    fun setGameRoomManagementService(@Lazy roomService: BlackjackRoomService) {
        this.roomService = roomService
    }
}