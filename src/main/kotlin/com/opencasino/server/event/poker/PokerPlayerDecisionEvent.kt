package com.opencasino.server.event.poker

import com.opencasino.server.event.AbstractEvent

open class PokerPlayerDecisionEvent(
    val inputId: String,
    val amount: Int? = null
) : AbstractEvent()