package com.opencasino.server.service.blackjack

import com.opencasino.server.network.pack.blackjack.shared.BlackjackUserSession
import com.opencasino.server.network.pack.blackjack.websocket.BlackjackWebSocketMessagePublisher
import org.reactivestreams.Subscription
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.*


interface BlackjackWebSocketSessionService : BlackjackWebSocketMessagePublisher {
    fun onActive(userSession: BlackjackUserSession): Flux<Any>
    fun onSubscribe(userSession: BlackjackUserSession, subscription: Subscription)
    fun onPrincipalInit(userSession: BlackjackUserSession, principal: Principal)
    fun onInactive(userSession: BlackjackUserSession)
    fun close(userSession: BlackjackUserSession): Mono<Void>
    fun close(userSessionId: String): Mono<Void>
    fun closeAll():Mono<Void>
    fun roomIds():Mono<Collection<String>>
    fun sessionIds():Mono<Collection<String>>
    fun roomSessionIds(roomId: UUID):Mono<Collection<String>>
}