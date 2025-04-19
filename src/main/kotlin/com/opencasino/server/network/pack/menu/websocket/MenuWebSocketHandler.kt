package com.opencasino.server.network.pack.menu.websocket

import com.google.gson.Gson
import com.opencasino.server.network.pack.update.MenuUpdatePack
import com.opencasino.server.service.MenuService
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

class MenuWebSocketHandler(private val menuService: MenuService) : WebSocketHandler {
    override fun handle(session: WebSocketSession): Mono<Void> {
        val menuUpdateEvent = MenuUpdatePack(
            availableGames = menuService.getAvailableGames(),
            totalActivePlayers = menuService.getTotalActivePlayers()
        )



        val jsonMessage = session.textMessage(convertToJson(menuUpdateEvent))
        return session.send(Mono.just(jsonMessage))
    }

    private fun convertToJson(menuUpdateEvent: MenuUpdatePack): String {
        return Gson().toJson(menuUpdateEvent)
    }
}