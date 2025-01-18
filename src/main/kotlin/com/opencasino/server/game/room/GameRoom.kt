package com.opencasino.server.game.room

import com.opencasino.server.game.Updatable
import com.opencasino.server.network.pack.blackjack.shared.BlackjackUserSession
import com.opencasino.server.network.websocket.WebSocketMessagePublisher
import java.util.*

interface GameRoom: Runnable, Updatable, WebSocketMessagePublisher {
    fun onRoomCreated(userSessions: List<BlackjackUserSession>)
    fun onRoomStarted()
    fun onGameStarted()
    fun onDestroy(userSessions: List<BlackjackUserSession>)
    fun onDisconnect(userSession: BlackjackUserSession): BlackjackUserSession
    fun sessions(): Collection<BlackjackUserSession>
    fun currentPlayersCount(): Int
    fun getPlayerSessionBySessionId(userSession: BlackjackUserSession): Optional<BlackjackUserSession>
    fun key(): UUID
    fun key(key: UUID)
    fun close(): Collection<BlackjackUserSession>
    fun onClose(userSession: BlackjackUserSession)
    fun schedule(runnable: Runnable, delayMillis: Long):Boolean
    fun schedulePeriodically(runnable: Runnable, initDelay:Long, loopRate:Long):Boolean
}