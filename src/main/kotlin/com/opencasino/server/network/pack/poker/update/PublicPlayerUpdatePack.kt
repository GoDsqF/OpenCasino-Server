package com.opencasino.server.network.pack.poker.update

import com.opencasino.server.network.pack.PublicUpdatePack
import com.opencasino.server.service.shared.PokerDecision

data class PublicPlayerUpdatePack(
    val id: Long,
    val position: Int,
    val displayName: String,
    val lastDecision: PokerDecision,
) : PublicUpdatePack
