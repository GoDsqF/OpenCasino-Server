package com.opencasino.server.network.pack.blackjack.update

import com.opencasino.server.game.model.Card
import com.opencasino.server.network.pack.UpdatePack


data class PlayerUpdatePack(
    val id: Long,
    val deck: List<Card>
): UpdatePack
