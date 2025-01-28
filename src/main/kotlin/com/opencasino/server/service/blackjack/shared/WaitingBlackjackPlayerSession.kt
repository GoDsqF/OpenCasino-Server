package com.opencasino.server.service.blackjack.shared

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.network.pack.blackjack.shared.BlackjackPlayerSession

data class WaitingBlackjackPlayerSession(
    val userSession: BlackjackPlayerSession,
    val initialData: GameRoomJoinEvent
)