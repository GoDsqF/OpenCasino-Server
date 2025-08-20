package com.opencasino.server.network.pack.poker.info

import com.opencasino.server.network.pack.InitPack


data class PlayerInfoPack(
    val id: Long,
    val balance: Double,
): InitPack