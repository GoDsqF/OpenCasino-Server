package com.opencasino.server.event

class PlayerDecisionEvent(val inputId: String, val state: Boolean):
    AbstractEvent()