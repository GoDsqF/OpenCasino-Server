package com.opencasino.server.service.shared

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.model.Player
import com.opencasino.server.network.shared.PlayerSession

abstract class WaitingPlayerSession(
    open val playerSession: PlayerSession<Player>,
    open val initialData: GameRoomJoinEvent
)