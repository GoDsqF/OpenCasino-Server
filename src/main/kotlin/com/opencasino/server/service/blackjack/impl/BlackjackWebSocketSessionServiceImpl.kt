package com.opencasino.server.service.blackjack.impl

import com.opencasino.server.config.FAILURE
import com.opencasino.server.config.MESSAGE
import com.opencasino.server.network.pack.blackjack.shared.BlackjackUserSession
import com.opencasino.server.network.pack.blackjack.update.GameUpdatePack
import com.opencasino.server.network.pack.shared.GameMessagePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.UserSession
import com.opencasino.server.service.blackjack.BlackjackRoomService
import com.opencasino.server.service.blackjack.BlackjackWebSocketSessionService
import com.opencasino.server.service.shared.MessageType
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.reactivestreams.Subscription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks.Many
import reactor.core.publisher.Sinks.many
import java.security.Principal
import java.util.*
import java.util.function.Function
import kotlin.collections.HashMap

@Service
class BlackjackWebSocketSessionServiceImpl : BlackjackWebSocketSessionService {
    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
    }

    private var sessionPublishers: MutableMap<String, Many<Any>> = HashMap()
    private var sessionSubscriptions: MutableMap<String, Subscription> = HashMap()
    private var sessions: MutableMap<String, BlackjackUserSession> = HashMap()
    private lateinit var roomService: BlackjackRoomService

    override fun sendBroadcast(message: Any) = sessionPublishers.values.forEach { it.tryEmitNext(message) }
    override fun close(userSession: BlackjackUserSession): Mono<Void> {
        if (sessionSubscriptions.containsKey(userSession.id))
            sessionSubscriptions[userSession.id]!!.cancel()
        return Mono.empty()
    }

    override fun close(userSessionId: String): Mono<Void> {
        if (sessions.containsKey(userSessionId))
            return close(sessions[userSessionId]!!)
        return Mono.empty()
    }

    override fun closeAll(): Mono<Void> {
        sessions.values.forEach(this::close)
        return Mono.empty()
    }

    override fun roomIds(): Mono<Collection<String>> = Mono.fromCallable {
        roomService.getRoomIds()
    }

    override fun sessionIds(): Mono<Collection<String>> = Mono.fromCallable {
        sessions.keys.toList()
    }

    override fun roomSessionIds(roomId: UUID): Mono<Collection<String>> = Mono.fromCallable {
        roomService.getRoomSessionIds(roomId)
    }

    override fun sendBroadcast(messageFunction: Function<BlackjackUserSession, Any>) =
        sessions.values.stream().forEach { this.send( it, messageFunction ) }

    override fun sendBroadcast(userSessions: Collection<BlackjackUserSession>, message: Any) =
        userSessions.forEach { send(it, message) }

    override fun send(
        userSession: BlackjackUserSession,
        function: Function<BlackjackUserSession, Any>
    ) =
        send(userSession, function.apply(userSession))

    override fun sendBroadcast(
        userSessions: Collection<BlackjackUserSession>,
        function: Function<BlackjackUserSession, Any>
    ) = userSessions.forEach { send(it, function.apply(it)) }

    override fun send(userSession: BlackjackUserSession, message: Any) {
        val webSocketSessionId = userSession.id
        if (sessionPublishers.containsKey(webSocketSessionId)) sessionPublishers[webSocketSessionId]!!
            .tryEmitNext(message)
    }

    override fun sendFailure(userSession: BlackjackUserSession, message: Any) =
        send(userSession, Message(FAILURE, message))

    override fun sendBroadcast(type: MessageType, message: String) =
        sendBroadcast(Message(MESSAGE, GameMessagePack(type.type, message)))

    override fun onActive(userSession: BlackjackUserSession): Flux<Any> {
        log.debug("Client ${userSession.id} connected")
        val sink = many().unicast().onBackpressureBuffer<Any>()
        sessionPublishers[userSession.id] = sink
        sessions[userSession.id] = userSession
        return sink.asFlux()
    }

    override fun onSubscribe(userSession: BlackjackUserSession, subscription: Subscription) {
        sessionSubscriptions[userSession.id] = subscription
    }

    override fun onPrincipalInit(
        userSession: BlackjackUserSession,
        principal: Principal
    ) {
        userSession.principal = principal
    }

    override fun onInactive(userSession: BlackjackUserSession) {
        log.debug("Client ${userSession.id} disconnected")
        if (!sessionPublishers.containsKey(userSession.id)) return
        sessionPublishers.remove(userSession.id)
        sessions.remove(userSession.id)

        if (userSession.roomKey != null)
            roomService.getRoomByKey(userSession.roomKey!!)
                .ifPresent { it.onDisconnect(userSession) }
    }

    @Autowired
    fun setGameRoomManagementService( @Lazy roomService: BlackjackRoomService) {
        this.roomService = roomService
    }
}