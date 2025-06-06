package com.opencasino.server.game.factory

import com.opencasino.server.game.blackjack.model.BlackjackPlayer


interface PlayerFactory<CM, P, GR, PS> {
    fun create(nextId: Long, initialData: CM, gameRoom: GR, playerSession: PS): BlackjackPlayer
}