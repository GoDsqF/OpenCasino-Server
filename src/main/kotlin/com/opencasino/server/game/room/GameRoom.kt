package com.opencasino.server.game.room

import com.opencasino.server.game.Updatable
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.websocket.WebSocketMessagePublisher
import java.util.*

interface GameRoom: Runnable, Updatable, WebSocketMessagePublisher {
    fun onRoomCreated(userSessions: List<PlayerSession>)
    fun onRoomStarted()
    fun onGameStarted()
    fun onDestroy(userSessions: List<PlayerSession>)
    fun onDisconnect(userSession: PlayerSession): PlayerSession
    fun sessions(): Collection<PlayerSession>
    fun currentPlayersCount(): Int
    fun getPlayerSessionBySessionId(userSession: PlayerSession): Optional<PlayerSession>
    fun key(): UUID
    fun key(key: UUID)
    fun close(): Collection<PlayerSession>
    fun onClose(userSession: PlayerSession)
    fun schedule(runnable: Runnable, delayMillis: Long):Boolean
    fun schedulePeriodically(runnable: Runnable, initDelay:Long, loopRate:Long):Boolean
}