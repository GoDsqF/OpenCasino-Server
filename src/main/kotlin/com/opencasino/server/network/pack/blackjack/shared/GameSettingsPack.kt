package com.opencasino.server.network.pack.blackjack.shared

import com.opencasino.server.network.pack.InitPack

data class GameSettingsPack(
    val loopRate: Long
): InitPack