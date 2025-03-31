package com.opencasino.server.service.blackjack

import com.opencasino.server.network.pack.blackjack.shared.BlackjackPlayerSession
import com.opencasino.server.network.websocket.BlackjackWebSocketMessagePublisher
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.*


interface BlackjackWebSocketSessionService : BlackjackWebSocketMessagePublisher {
    fun onActive(playerSession: BlackjackPlayerSession): Flux<Any>
    fun onSubscribe(playerSession: BlackjackPlayerSession, subscription: Subscription)
    fun onPrincipalInit(playerSession: BlackjackPlayerSession, principal: Principal)
    fun onInactive(playerSession: BlackjackPlayerSession)
    fun close(playerSession: BlackjackPlayerSession): Mono<Void>
    fun close(playerSessionId: String): Mono<Void>
    fun closeAll():Mono<Void>
    fun roomIds():Mono<Collection<String>>
    fun sessionIds():Mono<Collection<String>>
    fun roomSessionIds(roomId: UUID):Mono<Collection<String>>
}