package com.opencasino.server.event.poker

import com.opencasino.server.event.AbstractEvent

data class GameSettingsUpdateEvent(
    val betType: String?,
    val bet: Double,
    val minLimit: Double?,
    val maxLimit: Double?
) : AbstractEvent()