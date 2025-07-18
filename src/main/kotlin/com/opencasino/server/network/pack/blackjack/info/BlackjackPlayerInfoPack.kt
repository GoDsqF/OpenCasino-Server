package com.opencasino.server.network.pack.blackjack.info

import com.opencasino.server.network.pack.InitPack


data class BlackjackPlayerInfoPack(
    val id: Long,
    val balance: Double,
): InitPack