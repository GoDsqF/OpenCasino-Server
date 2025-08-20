package com.opencasino.server.game.factory

import com.opencasino.server.game.model.Player


interface PlayerFactory<CM, P, GR, PS> {
    fun create(nextId: Long, initialData: CM, gameRoom: GR, playerSession: PS): Player<Long>
}