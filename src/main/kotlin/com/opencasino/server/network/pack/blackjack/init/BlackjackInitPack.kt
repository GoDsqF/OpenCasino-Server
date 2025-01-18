package com.opencasino.server.network.pack.blackjack.init

import com.opencasino.server.network.pack.InitPack

data class BlackjackInitPack(
    val player: BlackjackPlayerInitPack,
    val loopRate: Long,
    val playersCount: Long
): InitPack