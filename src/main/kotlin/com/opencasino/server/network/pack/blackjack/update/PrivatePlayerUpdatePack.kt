package com.opencasino.server.network.pack.blackjack.update

import com.opencasino.server.network.pack.PrivateUpdatePack
import com.opencasino.server.service.shared.BlackjackDecision


data class PrivatePlayerUpdatePack(
    val id: Long,
    val balance: Double,
    val currentBet: Double,
    val lastDecision: BlackjackDecision,
    val availableActions: List<String>
): PrivateUpdatePack