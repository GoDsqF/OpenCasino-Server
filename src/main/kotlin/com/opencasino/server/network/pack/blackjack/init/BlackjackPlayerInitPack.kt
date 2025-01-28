package com.opencasino.server.network.pack.blackjack.init

import com.opencasino.server.network.pack.InitPack


data class BlackjackPlayerInitPack(
    val id: Long,
    val balance: Double,
): InitPack