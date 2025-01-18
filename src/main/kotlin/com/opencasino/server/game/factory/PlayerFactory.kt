package com.opencasino.server.game.factory

import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.model.Entity
import com.opencasino.server.game.model.Player
import com.opencasino.server.game.model.PlayerEntity
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.network.shared.UserSession
import java.util.*

interface PlayerFactory<CM, P, GR, PS> {
    fun create(nextId: UUID, initialData: CM, gameRoom: GR, playerSession: PS): BlackjackPlayer
}