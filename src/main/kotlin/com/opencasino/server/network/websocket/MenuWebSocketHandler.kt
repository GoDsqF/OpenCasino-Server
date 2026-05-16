package com.opencasino.server.network.websocket

import com.google.gson.Gson
import com.opencasino.server.service.MenuService
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class MenuWebSocketHandler(private val menuService: MenuService) : WebSocketHandler {

    private val gson = Gson()

    override fun handle(session: WebSocketSession): Mono<Void> {
        val snapshots = Flux.interval(Duration.ZERO, PUSH_INTERVAL)
            .map { menuService.getMenuSnapshot() }
            .map { session.textMessage(gson.toJson(it)) }
        return session.send(snapshots)
    }

    companion object {
        private val PUSH_INTERVAL: Duration = Duration.ofSeconds(5)
    }
}