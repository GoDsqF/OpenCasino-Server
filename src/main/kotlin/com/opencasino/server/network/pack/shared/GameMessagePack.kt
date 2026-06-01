package com.opencasino.server.network.pack.shared

import com.opencasino.server.network.pack.InitPack

data class GameMessagePack(
    val messageType: Int, // ts: GameMessageCategory
    val message: String,
) : InitPack
