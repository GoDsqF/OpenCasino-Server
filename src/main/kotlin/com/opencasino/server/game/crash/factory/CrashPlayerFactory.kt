package com.opencasino.server.game.crash.factory

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.crash.model.CrashPlayer
import com.opencasino.server.game.crash.room.AbstractCrashGameRoom
import com.opencasino.server.game.factory.PlayerFactory
import com.opencasino.server.game.model.Player
import com.opencasino.server.network.shared.PlayerSession
import org.springframework.stereotype.Component

@Component
class CrashPlayerFactory : PlayerFactory<GameRoomJoinEvent, CrashPlayer, AbstractCrashGameRoom, PlayerSession> {
    override fun create(
        nextId: Long,
        initialData: GameRoomJoinEvent,
        gameRoom: AbstractCrashGameRoom,
        playerSession: PlayerSession,
    ): Player<Long> = CrashPlayer(nextId, playerSession)
}
