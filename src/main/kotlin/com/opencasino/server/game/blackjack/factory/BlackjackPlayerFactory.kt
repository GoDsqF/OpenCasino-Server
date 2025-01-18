package com.opencasino.server.game.blackjack.factory

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.factory.PlayerFactory
import com.opencasino.server.game.model.Player
import com.opencasino.server.network.pack.blackjack.shared.BlackjackUserSession
import com.opencasino.server.network.shared.UserSession
import org.springframework.stereotype.Component
import java.util.*

@Component
class BlackjackPlayerFactory : PlayerFactory<GameRoomJoinEvent, BlackjackPlayer, BlackjackGameRoom, BlackjackUserSession> {
    override fun create(
        nextId: UUID,
        initialData: GameRoomJoinEvent,
        gameRoom: BlackjackGameRoom,
        playerSession: BlackjackUserSession
    ): BlackjackPlayer = BlackjackPlayer(nextId, gameRoom, playerSession)
}