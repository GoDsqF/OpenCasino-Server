package com.opencasino.server.event

data class GameRoomJoinEvent(
    val reconnectKey: String?,
    val playerUUID: String
): AbstractEvent()