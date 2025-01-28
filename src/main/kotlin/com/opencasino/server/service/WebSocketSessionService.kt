package com.opencasino.server.service

import com.opencasino.server.game.model.Player
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.websocket.WebSocketMessagePublisher
import org.reactivestreams.Subscription
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.*

@Service
interface WebSocketSessionService : WebSocketMessagePublisher {
    fun onActive(playerSession: PlayerSession<Player>): Flux<Any>
    fun onSubscribe(playerSession: PlayerSession<Player>, subscription: Subscription)
    fun onPrincipalInit(playerSession: PlayerSession<Player>, principal: Principal)
    fun onInactive(playerSession: PlayerSession<Player>)
    fun close(playerSession: PlayerSession<Player>): Mono<Void>
    fun close(userSessionId: String): Mono<Void>
    fun closeAll():Mono<Void>
    fun roomIds():Mono<Collection<String>>
    fun sessionIds():Mono<Collection<String>>
    fun roomSessionIds(roomId: UUID):Mono<Collection<String>>
}