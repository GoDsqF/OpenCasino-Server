package com.opencasino.server.event

class MenuUpdateEvent(
    val availableGames: List<String>,
    val totalActivePlayers: Int
) : AbstractEvent()