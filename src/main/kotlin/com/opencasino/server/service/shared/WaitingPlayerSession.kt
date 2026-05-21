package com.opencasino.server.service.shared

import com.opencasino.server.event.AbstractEvent
import com.opencasino.server.network.shared.PlayerSession

class WaitingPlayerSession(
    val playerSession: PlayerSession,
    val initialData: AbstractEvent,
)
