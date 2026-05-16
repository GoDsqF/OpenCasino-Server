package com.opencasino.server.network.pack.menu.update

import com.opencasino.server.network.pack.Pack

data class GameMetadata(
    val name: String,
    val activeRooms: Int,
    val activePlayers: Int
)

data class MenuUpdatePack(
    val games: List<GameMetadata>,
    val totalActivePlayers: Int
) : Pack