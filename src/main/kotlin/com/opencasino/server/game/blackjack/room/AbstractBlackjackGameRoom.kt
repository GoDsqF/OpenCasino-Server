package com.opencasino.server.game.blackjack.room

import com.opencasino.server.config.MESSAGE
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.network.pack.blackjack.shared.BlackjackPlayerSession
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.blackjack.BlackjackRoomService
import com.opencasino.server.service.blackjack.BlackjackWebSocketSessionService
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
    protected val roomService: BlackjackRoomService,
    protected val webSocketSessionService: BlackjackWebSocketSessionService
) : GameRoom {
    private val roomFutureList: MutableList<Disposable> = ArrayList()
    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
    }

    private var sessions: MutableMap<String, BlackjackPlayerSession> = HashMap()

    override fun onRoomCreated(userSessions: List<BlackjackPlayerSession>) {
        for (playerSession in userSessions) {
            this.sessions[playerSession.id] = playerSession
            sendBroadcast(Message(MESSAGE, playerSession.player!!.id.toString() + " successfully joined"))
        }
    }

    override fun onDestroy(userSessions: List<BlackjackPlayerSession>) {
        for (playerSession in userSessions) {
            this.sessions.remove(playerSession.id)
            sendBroadcast(Message(MESSAGE, playerSession.player!!.id.toString() + " left"))
        }
    }

    override fun schedule(runnable: Runnable, delayMillis: Long)=
        roomFutureList.add(schedulerService.schedule(runnable, delayMillis, TimeUnit.MILLISECONDS))

    override fun schedulePeriodically(runnable: Runnable, initDelay: Long, loopRate: Long)=
        roomFutureList.add(schedulerService.schedulePeriodically(runnable,initDelay, loopRate,TimeUnit.MILLISECONDS))

    override fun onDisconnect(userSession: BlackjackPlayerSession): BlackjackPlayerSession =
        sessions.remove(userSession.id)!!

    override fun send(userSession: BlackjackPlayerSession, message: Any) =
        webSocketSessionService.send(userSession, message)

    override fun sendBroadcast(message: Any) =
        webSocketSessionService.sendBroadcast(sessions.values, message)

    override fun sendBroadcast(messageFunction: Function<BlackjackPlayerSession, Any>) =
        webSocketSessionService.sendBroadcast(sessions.values, messageFunction)

    override fun sendFailure(userSession: BlackjackPlayerSession, message: Any) {
        webSocketSessionService.sendFailure(userSession, message)
    }

    override fun sendBroadcast(type: MessageType, message: String) {
        webSocketSessionService.sendBroadcast(type, message)
    }

    override fun sendBroadcast(userSessions: Collection<BlackjackPlayerSession>, message: Any) {
        webSocketSessionService.sendBroadcast(userSessions, message)
    }

    override fun send(userSession: BlackjackPlayerSession, function: Function<BlackjackPlayerSession, Any>) {
        webSocketSessionService.send(userSession, function)
    }

    override fun sendBroadcast(
        userSessions: Collection<BlackjackPlayerSession>,
        function: Function<BlackjackPlayerSession, Any>
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

    override fun close(): Collection<BlackjackPlayerSession> {
        val result: Collection<BlackjackPlayerSession> = sessions.values
        sessions.values.forEach { this.onClose(it) }
        roomFutureList.forEach { it.dispose() }
        log.trace("Room {} has been closed", key())
        return result
    }

    override fun onClose(userSession: BlackjackPlayerSession) {}
    override fun getPlayerSessionBySessionId(userSession: BlackjackPlayerSession): Optional<BlackjackPlayerSession> =
        if (sessions.containsKey(userSession.id)) Optional.of(sessions[userSession.id]!!)
        else Optional.empty()

    override fun sessions(): Collection<BlackjackPlayerSession> = sessions.values
    override fun currentPlayersCount(): Int = sessions.size
    override fun key(): UUID = gameRoomId
    override fun key(key: UUID) {
        this.gameRoomId = key
    }
}