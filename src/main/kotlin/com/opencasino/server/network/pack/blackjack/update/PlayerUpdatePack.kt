package com.opencasino.server.network.pack.blackjack.update

import com.opencasino.server.game.model.Card
import com.opencasino.server.network.pack.UpdatePack
import java.util.UUID

data class PlayerUpdatePack(
    val id: UUID,
    val deck: List<Card>
): UpdatePack
