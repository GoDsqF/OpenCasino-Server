package com.opencasino.server.service.shared

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.network.shared.PlayerSession

data class WaitingBlackjackPlayerSession(
    val playerSession: PlayerSession,
    val initialData: GameRoomJoinEvent
)