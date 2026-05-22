package com.opencasino.server.game.room

import com.opencasino.server.game.Updatable
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.websocket.WebSocketMessagePublisher
import java.util.*

interface GameRoom :
    Runnable,
    Updatable,
    WebSocketMessagePublisher {
    fun onRoomCreated(userSessions: List<PlayerSession>)

    fun onRoomStarted()

    fun onGameStarted()

    fun onDestroy(userSessions: List<PlayerSession>)

    fun onDisconnect(userSession: PlayerSession): PlayerSession

    /**
     * Player explicitly left the table via GAME_ROOM_LEAVE (type=22). Differs from
     * onDisconnect only in that it bypasses the 60s reconnect grace — the leaver
     * has signalled intent, so cash-out/settle and seat-release happen immediately.
     * Default impl delegates to onDisconnect, which already does the right thing
     * for both BJ (auto-settle if mid-round) and Poker (auto-fold + cash-out).
     * onClose is invoked first so the leaver gets a GAME_ROOM_CLOSE confirmation
     * before the room tears down their seat.
     */
    fun onPlayerLeave(userSession: PlayerSession): PlayerSession {
        onClose(userSession)
        return onDisconnect(userSession)
    }

    fun onGraceStart(userSession: PlayerSession) {}

    fun onReattach(
        oldSession: PlayerSession,
        newSession: PlayerSession,
    ) {}

    fun sessions(): Collection<PlayerSession>

    fun currentPlayersCount(): Int

    fun getPlayerSessionBySessionId(userSession: PlayerSession): Optional<PlayerSession>

    fun key(): UUID

    fun key(key: UUID)

    fun close(): Collection<PlayerSession>

    fun onClose(userSession: PlayerSession)

    fun schedule(
        runnable: Runnable,
        delayMillis: Long,
    ): Boolean

    fun schedulePeriodically(
        runnable: Runnable,
        initDelay: Long,
        loopRate: Long,
    ): Boolean

    fun onPlayerInfoRequest(userSession: PlayerSession)
}
