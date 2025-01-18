package com.opencasino.server.service.blackjack.shared

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.network.pack.blackjack.shared.BlackjackUserSession
import com.opencasino.server.service.shared.WaitingPlayerSession

data class WaitingBlackjackPlayerSession(
    val userSession: BlackjackUserSession,
    val initialData: GameRoomJoinEvent
)