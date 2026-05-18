package com.opencasino.server.network.pack.menu.update

data class PokerRoomSummary(
    val roomId: String,
    val betType: String,
    val bet: Double,
    val smallBlind: Double,
    val bigBlind: Double,
    val currentPlayers: Int,
    val maxPlayers: Int,
    val phase: String,
)