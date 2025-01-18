package com.opencasino.server.event

data class GameRoomJoinEvent(
    val reconnectKey: String?,
    val playerName: String
): AbstractEvent()