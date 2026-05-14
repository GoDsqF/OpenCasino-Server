package com.opencasino.server.network.pack.blackjack.shared

import com.opencasino.server.network.pack.InitPack

data class GameSettingsPack(
    val roomId: String,
    val loopRate: Long
): InitPack