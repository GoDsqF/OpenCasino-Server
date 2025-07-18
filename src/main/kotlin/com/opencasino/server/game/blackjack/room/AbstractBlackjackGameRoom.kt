package com.opencasino.server.game.blackjack.room

import com.opencasino.server.config.MESSAGE
import com.opencasino.server.event.BetEvent
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.MessageType
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import reactor.core.Disposable
import reactor.core.scheduler.Scheduler
import java.util.*
import java.util.function.Function
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

abstract class AbstractBlackjackGameRoom protected constructor(
    private var gameRoomId: UUID,
    private val schedulerService: Scheduler,
    protected val roomService: RoomService,
    protected val webSocketSessionService: WebSocketSessionService
) : GameRoom {
    private val roomFutureList: MutableList<Disposable> = ArrayList()
    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
    }

    private var sessions: MutableMap<String, PlayerSession> = HashMap()

    override fun onRoomCreated(userSessions: List<PlayerSession>) {
        for (playerSession in userSessions) {
            this.sessions[playerSession.id] = playerSession
            sendBroadcast(Message(MESSAGE, playerSession.player!!.id.toString() + " successfully joined"))
        }
    }

    override fun onDestroy(userSessions: List<PlayerSession>) {
        for (playerSession in userSessions) {
            this.sessions.remove(playerSession.id)
            sendBroadcast(Message(MESSAGE, playerSession.player!!.id.toString() + " left"))
        }
    }

    override fun schedule(runnable: Runnable, delayMillis: Long)=
        roomFutureList.add(schedulerService.schedule(runnable, delayMillis, TimeUnit.MILLISECONDS))

    override fun schedulePeriodically(runnable: Runnable, initDelay: Long, loopRate: Long)=
        roomFutureList.add(schedulerService.schedulePeriodically(runnable,initDelay, loopRate,TimeUnit.MILLISECONDS))

    override fun onDisconnect(userSession: PlayerSession): PlayerSession =
        sessions.remove(userSession.id)!!

    override fun send(userSession: PlayerSession, message: Any) =
        webSocketSessionService.send(userSession, message)

    override fun sendBroadcast(message: Any) =
        webSocketSessionService.sendBroadcast(sessions.values, message)

    override fun sendBroadcast(messageFunction: Function<PlayerSession, Any>) =
        webSocketSessionService.sendBroadcast(sessions.values, messageFunction)

    override fun sendFailure(userSession: PlayerSession, message: Any) {
        webSocketSessionService.sendFailure(userSession, message)
    }

    override fun sendBroadcast(type: MessageType, message: String) {
        webSocketSessionService.sendBroadcast(type, message)
    }

    override fun sendBroadcast(userSessions: Collection<PlayerSession>, message: Any) {
        webSocketSessionService.sendBroadcast(userSessions, message)
    }

    override fun send(userSession: PlayerSession, function: Function<PlayerSession, Any>) {
        webSocketSessionService.send(userSession, function)
    }

    override fun sendBroadcast(
        userSessions: Collection<PlayerSession>,
        function: Function<PlayerSession, Any>
    ) {
        webSocketSessionService.sendBroadcast(userSessions, function)
    }

    override fun run() {
        try {
            update()
        } catch (e: Exception) {
            log.error("room update exception", e)
        }
    }

    override fun close(): Collection<PlayerSession> {
        val result: Collection<PlayerSession> = sessions.values
        sessions.values.forEach { this.onClose(it) }
        roomFutureList.forEach { it.dispose() }
        log.trace("Room {} has been closed", key())
        return result
    }

    override fun onClose(userSession: PlayerSession) {}
    override fun getPlayerSessionBySessionId(userSession: PlayerSession): Optional<PlayerSession> =
        if (sessions.containsKey(userSession.id)) Optional.of(sessions[userSession.id]!!)
        else Optional.empty()

    override fun sessions(): Collection<PlayerSession> = sessions.values
    override fun currentPlayersCount(): Int = sessions.size
    override fun key(): UUID = gameRoomId
    override fun key(key: UUID) {
        this.gameRoomId = key
    }
}