package com.opencasino.server.service.blackjack.impl

import com.opencasino.server.config.FAILURE
import com.opencasino.server.config.MESSAGE
import com.opencasino.server.network.pack.blackjack.shared.BlackjackPlayerSession
import com.opencasino.server.network.pack.shared.GameMessagePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.blackjack.BlackjackRoomService
import com.opencasino.server.service.blackjack.BlackjackWebSocketSessionService
import com.opencasino.server.service.shared.MessageType
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.reactivestreams.Subscription
import org.springframework.beans.factory.annotation.Autowired
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
    private var sessions: MutableMap<String, BlackjackPlayerSession> = HashMap()
    private lateinit var roomService: BlackjackRoomService

    override fun sendBroadcast(message: Any) = sessionPublishers.values.forEach { it.tryEmitNext(message) }
    override fun close(playerSession: BlackjackPlayerSession): Mono<Void> {
        if (sessionSubscriptions.containsKey(playerSession.id))
            sessionSubscriptions[playerSession.id]!!.cancel()
        return Mono.empty()
    }

    override fun close(playerSessionId: String): Mono<Void> {
        if (sessions.containsKey(playerSessionId))
            return close(sessions[playerSessionId]!!)
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

    override fun sendBroadcast(messageFunction: Function<BlackjackPlayerSession, Any>) =
        sessions.values.stream().forEach { this.send( it, messageFunction ) }

    override fun sendBroadcast(userSessions: Collection<BlackjackPlayerSession>, message: Any) =
        userSessions.forEach { send(it, message) }

    override fun send(
        userSession: BlackjackPlayerSession,
        function: Function<BlackjackPlayerSession, Any>
    ) =
        send(userSession, function.apply(userSession))

    override fun sendBroadcast(
        userSessions: Collection<BlackjackPlayerSession>,
        function: Function<BlackjackPlayerSession, Any>
    ) = userSessions.forEach { send(it, function.apply(it)) }

    override fun send(userSession: BlackjackPlayerSession, message: Any) {
        val webSocketSessionId = userSession.id
        if (sessionPublishers.containsKey(webSocketSessionId)) sessionPublishers[webSocketSessionId]!!
            .tryEmitNext(message)
    }

    override fun sendFailure(userSession: BlackjackPlayerSession, message: Any) =
        send(userSession, Message(FAILURE, message))

    override fun sendBroadcast(type: MessageType, message: String) =
        sendBroadcast(Message(MESSAGE, GameMessagePack(type.type, message)))

    override fun onActive(playerSession: BlackjackPlayerSession): Flux<Any> {
        log.debug("Client ${playerSession.id} connected")
        val sink = many().unicast().onBackpressureBuffer<Any>()
        sessionPublishers[playerSession.id] = sink
        sessions[playerSession.id] = playerSession
        return sink.asFlux()
    }

    override fun onSubscribe(playerSession: BlackjackPlayerSession, subscription: Subscription) {
        sessionSubscriptions[playerSession.id] = subscription
    }

    override fun onPrincipalInit(
        playerSession: BlackjackPlayerSession,
        principal: Principal
    ) {
        playerSession.principal = principal
    }

    override fun onInactive(playerSession: BlackjackPlayerSession) {
        log.debug("Client ${playerSession.id} disconnected")
        if (!sessionPublishers.containsKey(playerSession.id)) return
        sessionPublishers.remove(playerSession.id)
        sessions.remove(playerSession.id)

        if (playerSession.roomKey != null)
            roomService.getRoomByKey(playerSession.roomKey!!)
                .ifPresent { it.onDisconnect(playerSession) }
    }

    @Autowired
    fun setGameRoomManagementService( @Lazy roomService: BlackjackRoomService) {
        this.roomService = roomService
    }
}