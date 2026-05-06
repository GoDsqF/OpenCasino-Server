package com.opencasino.server.event.poker

import com.opencasino.server.event.AbstractEvent

data class GameRoomCreateEvent(
    val playerUUID: String,
    val settings: GameSettingsUpdateEvent
) : AbstractEvent()
