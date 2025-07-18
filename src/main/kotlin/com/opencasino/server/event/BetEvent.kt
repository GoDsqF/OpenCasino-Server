package com.opencasino.server.event

data class BetEvent(
    val bet: Double
): AbstractEvent()