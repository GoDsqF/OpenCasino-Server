package com.opencasino.server.event.poker

import com.opencasino.server.event.AbstractEvent

data class GameRoomCreateEvent(
    /** @deprecated Phase 5: см. GameRoomJoinEvent.playerUUID. */
    val playerUUID: String,
    val settings: GameSettingsUpdateEvent,
) : AbstractEvent()
