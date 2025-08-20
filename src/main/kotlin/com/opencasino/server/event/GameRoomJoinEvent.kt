package com.opencasino.server.event

data class GameRoomJoinEvent(
    val gameId: String,
    val reconnectKey: String?,
    val playerUUID: String
): AbstractEvent()