package com.opencasino.server.network.pack.update

import com.opencasino.server.network.pack.PrivateUpdatePack
import com.opencasino.server.service.shared.BlackjackDecision


data class BlackjackPrivatePlayerUpdatePack(
    val id: Long,
    val decision: BlackjackDecision
): PrivateUpdatePack