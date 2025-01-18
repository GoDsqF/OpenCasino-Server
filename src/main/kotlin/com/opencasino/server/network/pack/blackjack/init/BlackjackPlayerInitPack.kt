package com.opencasino.server.network.pack.blackjack.init

import com.opencasino.server.network.pack.InitPack
import java.util.*

data class BlackjackPlayerInitPack(
    val id: UUID,
    val balance: Double,
): InitPack