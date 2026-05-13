package com.opencasino.server.network.pack.blackjack.update

import com.opencasino.server.network.pack.PublicUpdatePack
import com.opencasino.server.service.shared.BlackjackDecision


data class PublicPlayerUpdatePack(
    val id: Long,
    val lastDecision: BlackjackDecision
) : PublicUpdatePack