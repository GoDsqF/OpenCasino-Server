package com.opencasino.server.network.pack.poker.shared

import com.opencasino.server.network.pack.InitPack

data class RoomPack(
    val timestamp: Long,
    val roomId: String
): InitPack