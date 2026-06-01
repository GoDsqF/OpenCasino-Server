package com.opencasino.server.event

open class BlackjackPlayerDecisionEvent(
    val inputId: String, // ts: BlackjackDecisionName
) : AbstractEvent()
