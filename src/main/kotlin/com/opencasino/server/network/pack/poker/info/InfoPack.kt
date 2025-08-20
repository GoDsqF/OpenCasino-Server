package com.opencasino.server.network.pack.poker.info

import com.opencasino.server.network.pack.InitPack

data class InfoPack(
    val player: PlayerInfoPack,
    val loopRate: Long,
    val playersCount: Long
): InitPack