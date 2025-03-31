package com.opencasino.server.network.pack.menu.websocket

import com.google.gson.Gson
import com.opencasino.server.event.MenuUpdateEvent
import com.opencasino.server.service.MenuService
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

class MenuWebSocketHandler(private val menuService: MenuService) : WebSocketHandler {
    override fun handle(session: WebSocketSession): Mono<Void> {
        val menuUpdateEvent = MenuUpdateEvent(
            availableGames = menuService.getAvailableGames(),
            totalActivePlayers = menuService.getTotalActivePlayers()
        )



        val jsonMessage = session.textMessage(convertToJson(menuUpdateEvent))
        return session.send(Mono.just(jsonMessage))
    }

    private fun convertToJson(menuUpdateEvent: MenuUpdateEvent): String {
        return Gson().toJson(menuUpdateEvent)
    }
}