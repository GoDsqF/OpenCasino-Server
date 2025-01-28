package com.opencasino.server.game.room

import com.opencasino.server.game.Updatable
import com.opencasino.server.network.pack.blackjack.shared.BlackjackPlayerSession
import com.opencasino.server.network.websocket.WebSocketMessagePublisher
import java.util.*

interface GameRoom: Runnable, Updatable, WebSocketMessagePublisher {
    fun onRoomCreated(userSessions: List<BlackjackPlayerSession>)
    fun onRoomStarted()
    fun onGameStarted()
    fun onDestroy(userSessions: List<BlackjackPlayerSession>)
    fun onDisconnect(userSession: BlackjackPlayerSession): BlackjackPlayerSession
    fun sessions(): Collection<BlackjackPlayerSession>
    fun currentPlayersCount(): Int
    fun getPlayerSessionBySessionId(userSession: BlackjackPlayerSession): Optional<BlackjackPlayerSession>
    fun key(): UUID
    fun key(key: UUID)
    fun close(): Collection<BlackjackPlayerSession>
    fun onClose(userSession: BlackjackPlayerSession)
    fun schedule(runnable: Runnable, delayMillis: Long):Boolean
    fun schedulePeriodically(runnable: Runnable, initDelay:Long, loopRate:Long):Boolean
}