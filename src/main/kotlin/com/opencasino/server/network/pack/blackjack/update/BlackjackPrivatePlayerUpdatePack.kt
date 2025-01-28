package com.opencasino.server.network.pack.blackjack.update

import com.opencasino.server.network.pack.PrivateUpdatePack
import com.opencasino.server.service.blackjack.shared.BlackjackDecision
import java.util.*

data class BlackjackPrivatePlayerUpdatePack(
    val id: Long,
    val decision: BlackjackDecision
): PrivateUpdatePack