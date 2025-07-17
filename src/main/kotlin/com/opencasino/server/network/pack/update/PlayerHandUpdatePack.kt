package com.opencasino.server.network.pack.update

import com.opencasino.server.game.model.Card
import com.opencasino.server.network.pack.UpdatePack


data class PlayerHandUpdatePack(
    val id: Long,
    val deck: List<Card>
): UpdatePack
