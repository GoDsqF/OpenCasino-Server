package com.opencasino.server.network.pack.poker.update

import com.opencasino.server.network.pack.PrivateUpdatePack
import com.opencasino.server.service.shared.PokerDecision


data class PrivatePlayerUpdatePack(
    val id: Long,
    val position: Int,
    val lastDecision: PokerDecision
): PrivateUpdatePack