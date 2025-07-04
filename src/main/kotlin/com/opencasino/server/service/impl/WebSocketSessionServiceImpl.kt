package com.opencasino.server.service.impl

import com.opencasino.server.config.FAILURE
import com.opencasino.server.config.MESSAGE
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.pack.shared.GameMessagePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
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
class WebSocketSessionServiceImpl : WebSocketSessionService {
    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
    }

    private var sessionPublishers: MutableMap<String, Many<Any>> = HashMap()
    private var sessionSubscriptions: MutableMap<String, Subscription> = HashMap()
    private var sessions: MutableMap<String, PlayerSession> = HashMap()
    private lateinit var roomService: RoomService

    override fun sendBroadcast(message: Any) = sessionPublishers.values.forEach { it.tryEmitNext(message) }
    override fun close(playerSession: PlayerSession): Mono<Void> {
        if (sessionSubscriptions.containsKey(playerSession.id))
            sessionSubscriptions[playerSession.id]!!.cancel()
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

    override fun sendBroadcast(messageFunction: Function<PlayerSession, Any>) =
        sessions.values.stream().forEach { this.send( it, messageFunction ) }

    override fun sendBroadcast(userSessions: Collection<PlayerSession>, message: Any) =
        userSessions.forEach { send(it, message) }

    override fun send(
        userSession: PlayerSession,
        function: Function<PlayerSession, Any>
    ) =
        send(userSession, function.apply(userSession))

    override fun sendBroadcast(
        userSessions: Collection<PlayerSession>,
        function: Function<PlayerSession, Any>
    ) = userSessions.forEach { send(it, function.apply(it)) }

    override fun send(userSession: PlayerSession, message: Any) {
        val webSocketSessionId = userSession.id
        if (sessionPublishers.containsKey(webSocketSessionId)) sessionPublishers[webSocketSessionId]!!
            .tryEmitNext(message)
    }

    override fun sendFailure(userSession: PlayerSession, message: Any) =
        send(userSession, Message(FAILURE, message))

    override fun sendBroadcast(type: MessageType, message: String) =
        sendBroadcast(Message(MESSAGE, GameMessagePack(type.type, message)))

    override fun onActive(playerSession: PlayerSession): Flux<Any> {
        log.debug("Client ${playerSession.id} connected")
        val sink = many().unicast().onBackpressureBuffer<Any>()
        sessionPublishers[playerSession.id] = sink
        sessions[playerSession.id] = playerSession
        return sink.asFlux()
    }

    override fun onSubscribe(playerSession: PlayerSession, subscription: Subscription) {
        sessionSubscriptions[playerSession.id] = subscription
    }

    override fun onPrincipalInit(
        playerSession: PlayerSession,
        principal: Principal
    ) {
        playerSession.principal = principal
    }

    override fun onInactive(playerSession: PlayerSession) {
        log.debug("Client ${playerSession.id} disconnected")
        if (!sessionPublishers.containsKey(playerSession.id)) return
        sessionPublishers.remove(playerSession.id)
        sessions.remove(playerSession.id)

        if (playerSession.roomKey != null)
            roomService.getRoomByKey(playerSession.roomKey!!)
                .ifPresent { it.onDisconnect(playerSession) }
    }

    @Autowired
    fun setGameRoomManagementService( @Lazy roomService: RoomService) {
        this.roomService = roomService
    }
}