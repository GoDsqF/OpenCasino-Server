package com.opencasino.server.event.poker

import com.opencasino.server.event.AbstractEvent

open class PokerPlayerDecisionEvent(
    val inputId: String, // ts: PokerDecisionName
    // Обязательно для CALL/RAISE/ALL_IN.
    val amount: Double? = null,
) : AbstractEvent()
