package com.opencasino.server.service

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
    fun onActive(playerSession: PlayerSession): Flux<Any>

    fun onSubscribe(
        playerSession: PlayerSession,
        subscription: Subscription,
    )

    fun onPrincipalInit(
        playerSession: PlayerSession,
        principal: Principal,
    )

    fun onInactive(playerSession: PlayerSession)

    /**
     * Player asked to leave the table explicitly (GAME_ROOM_LEAVE, type=22).
     * The WS stays open — only the room binding is cleared so the same socket
     * can be used to join another table. Bypasses the 60s reconnect grace:
     * the leaver has signalled intent, so settle/cash-out happens immediately.
     */
    fun onPlayerLeave(playerSession: PlayerSession)

    fun close(playerSession: PlayerSession): Mono<Void>

    fun close(userSessionId: String): Mono<Void>

    fun closeAll(): Mono<Void>

    fun roomIds(): Mono<Collection<String>>

    fun sessionIds(): Mono<Collection<String>>

    fun roomSessionIds(roomId: UUID): Mono<Collection<String>>
}
