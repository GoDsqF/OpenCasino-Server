package com.opencasino.server.game.poker.holdem.factory

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.factory.PlayerFactory
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.game.poker.holdem.room.PokerGameRoom
import com.opencasino.server.network.shared.PlayerSession
import org.springframework.stereotype.Component

@Component
class PokerPlayerFactory : PlayerFactory<GameRoomJoinEvent, PokerPlayer, PokerGameRoom, PlayerSession> {
    override fun create(
        nextId: Long,
        initialData: GameRoomJoinEvent,
        gameRoom: PokerGameRoom,
        playerSession: PlayerSession
    ): PokerPlayer = PokerPlayer(nextId, gameRoom, playerSession)
}