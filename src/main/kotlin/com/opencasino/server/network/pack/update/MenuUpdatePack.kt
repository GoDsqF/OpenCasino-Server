package com.opencasino.server.network.pack.update

import com.opencasino.server.event.AbstractEvent

class MenuUpdatePack(
    val availableGames: List<String>,
    val totalActivePlayers: Int
) : AbstractEvent()