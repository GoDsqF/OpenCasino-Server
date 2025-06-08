package com.opencasino.server.game.blackjack.factory

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.factory.PlayerFactory
import com.opencasino.server.network.shared.PlayerSession
import org.springframework.stereotype.Component
import java.util.*

@Component
class BlackjackPlayerFactory : PlayerFactory<GameRoomJoinEvent, BlackjackPlayer, BlackjackGameRoom, PlayerSession> {
    override fun create(
        nextId: Long,
        initialData: GameRoomJoinEvent,
        gameRoom: BlackjackGameRoom,
        playerSession: PlayerSession
    ): BlackjackPlayer = BlackjackPlayer(nextId, gameRoom, playerSession)
}