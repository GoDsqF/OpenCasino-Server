package com.opencasino.server.service.shared

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.model.Player
import com.opencasino.server.network.shared.PlayerSession

class WaitingPlayerSession(
    val playerSession: PlayerSession,
    val initialData: GameRoomJoinEvent
)