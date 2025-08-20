package com.opencasino.server.network.pack.poker.update

import com.opencasino.server.game.model.Card
import com.opencasino.server.network.pack.UpdatePack


data class deprecatedPlayerHandUpdatePack(
    val id: Long,
    val position: Int,
    val stack: Int,
    val cards: List<Card>
): UpdatePack
