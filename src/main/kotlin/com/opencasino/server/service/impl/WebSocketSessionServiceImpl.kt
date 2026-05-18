package com.opencasino.server.service.impl

import com.opencasino.server.config.BET_FAILURE
import com.opencasino.server.config.DISCONNECT_GRACE_MS
import com.opencasino.server.config.FAILURE
import com.opencasino.server.config.GAME_ROOM_JOIN_FAILURE
import com.opencasino.server.config.MESSAGE
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.pack.shared.FailurePack
import com.opencasino.server.network.pack.shared.GameMessagePack
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.FailureCode
import com.opencasino.server.service.shared.MessageType
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.reactivestreams.Subscription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks.Many
import reactor.core.publisher.Sinks.many
import reactor.core.scheduler.Scheduler
import java.security.Principal
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
    private val pendingDisconnects: MutableMap<UUID, PendingDisconnect> = ConcurrentHashMap()
    private lateinit var blackjackRoomService: RoomService
    private lateinit var pokerRoomService: RoomService
    private lateinit var schedulerService: Scheduler

    var disconnectGraceMs: Long = DISCONNECT_GRACE_MS

    fun overrideSchedulerForTests(scheduler: Scheduler) {
        schedulerService = scheduler
    }

    private data class PendingDisconnect(
        val oldSession: PlayerSession,
        val task: Disposable,
    )

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
        blackjackRoomService.getRoomIds()
    }

    override fun sessionIds(): Mono<Collection<String>> = Mono.fromCallable {
        sessions.keys.toList()
    }

    override fun roomSessionIds(roomId: UUID): Mono<Collection<String>> = Mono.fromCallable {
        blackjackRoomService.getRoomSessionIds(roomId)
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

    override fun sendFailure(userSession: PlayerSession, code: FailureCode, message: String, details: Any?) =
        send(userSession, Message(FAILURE, FailurePack(code.name, message, details)))

    override fun sendBetFailure(userSession: PlayerSession, code: FailureCode, message: String, details: Any?) =
        send(userSession, Message(BET_FAILURE, FailurePack(code.name, message, details)))

    override fun sendJoinFailure(userSession: PlayerSession, code: FailureCode, message: String, details: Any?) =
        send(userSession, Message(GAME_ROOM_JOIN_FAILURE, FailurePack(code.name, message, details)))

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
        val userId = playerSession.userId ?: return
        val pending = pendingDisconnects.remove(userId) ?: return
        pending.task.dispose()
        reattach(pending.oldSession, playerSession)
    }

    private fun reattach(oldSession: PlayerSession, newSession: PlayerSession) {
        val service = oldSession.serviceId
        val roomKey = oldSession.roomKey
        if (service == null || roomKey == null) {
            sessions.remove(oldSession.id)
            return
        }
        val room = lookupRoom(service, roomKey)
        if (room == null) {
            sessions.remove(oldSession.id)
            return
        }
        newSession.roomKey = roomKey
        newSession.serviceId = service
        newSession.player = oldSession.player
        room.onReattach(oldSession, newSession)
        sessions.remove(oldSession.id)
        log.debug("Reattached user {} from {} to {}", newSession.userId, oldSession.id, newSession.id)
    }

    override fun onInactive(playerSession: PlayerSession) {
        log.debug("Client ${playerSession.id} disconnected")
        sessionSubscriptions.remove(playerSession.id)
        if (!sessionPublishers.containsKey(playerSession.id)) return
        sessionPublishers.remove(playerSession.id)

        val userId = playerSession.userId
        val roomKey = playerSession.roomKey
        val service = playerSession.serviceId
        val room = if (roomKey != null && service != null) lookupRoom(service, roomKey) else null

        // Anonymous, no room, or room already gone: settle immediately.
        if (userId == null || room == null) {
            sessions.remove(playerSession.id)
            if (room != null) room.onDisconnect(playerSession)
            return
        }

        // Cancel any earlier pending for the same user (rapid reconnect-then-disconnect).
        pendingDisconnects.remove(userId)?.task?.dispose()

        val task = schedulerService.schedule(
            { expireDisconnect(playerSession, userId) },
            disconnectGraceMs, TimeUnit.MILLISECONDS,
        )
        pendingDisconnects[userId] = PendingDisconnect(playerSession, task)
        try {
            room.onGraceStart(playerSession)
        } catch (e: Exception) {
            log.error("onGraceStart failed for {}", playerSession.id, e)
        }
    }

    private fun expireDisconnect(oldSession: PlayerSession, userId: UUID) {
        val pending = pendingDisconnects[userId] ?: return
        if (pending.oldSession.id != oldSession.id) return
        pendingDisconnects.remove(userId)
        sessions.remove(oldSession.id)
        val service = oldSession.serviceId ?: return
        val roomKey = oldSession.roomKey ?: return
        lookupRoom(service, roomKey)?.onDisconnect(oldSession)
    }

    private fun lookupRoom(service: String, roomKey: UUID): GameRoom? = when (service) {
        "Blackjack" -> blackjackRoomService.getRoomByKey(roomKey).orElse(null)
        "Poker" -> pokerRoomService.getRoomByKey(roomKey).orElse(null)
        else -> null
    }

    @Autowired
    @Qualifier("blackjackRoomServiceImpl")
    fun setBlackjackGameRoomManagementService(@Lazy roomService: RoomService) {
        this.blackjackRoomService = roomService
    }

    @Autowired
    @Qualifier("pokerRoomServiceImpl")
    fun setPokerGameRoomManagementService( @Lazy roomService: RoomService) {
        this.pokerRoomService = roomService
    }

    @Autowired
    fun setScheduler(@Lazy scheduler: Scheduler) {
        this.schedulerService = scheduler
    }
}