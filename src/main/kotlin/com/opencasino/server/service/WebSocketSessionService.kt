package com.opencasino.server.service

import com.opencasino.server.game.model.Player
import com.opencasino.server.network.shared.UserSession
import com.opencasino.server.network.websocket.WebSocketMessagePublisher
import org.reactivestreams.Subscription
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.*

@Service
interface WebSocketSessionService : WebSocketMessagePublisher {
    fun onActive(userSession: UserSession<Player>): Flux<Any>
    fun onSubscribe(userSession: UserSession<Player>, subscription: Subscription)
    fun onPrincipalInit(userSession: UserSession<Player>, principal: Principal)
    fun onInactive(userSession: UserSession<Player>)
    fun close(userSession: UserSession<Player>): Mono<Void>
    fun close(userSessionId: String): Mono<Void>
    fun closeAll():Mono<Void>
    fun roomIds():Mono<Collection<String>>
    fun sessionIds():Mono<Collection<String>>
    fun roomSessionIds(roomId: UUID):Mono<Collection<String>>
}