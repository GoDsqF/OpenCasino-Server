package com.opencasino.server.service.shared

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.model.Player
import com.opencasino.server.network.shared.UserSession

abstract class WaitingPlayerSession(
    open val userSession: UserSession<Player>,
    open val initialData: GameRoomJoinEvent
)