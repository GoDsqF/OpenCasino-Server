package com.opencasino.server.network.pack.blackjack.info

import com.opencasino.server.network.pack.InitPack

data class BlackjackInfoPack(
    val player: BlackjackPlayerInfoPack,
    val loopRate: Long,
    val playersCount: Long
): InitPack