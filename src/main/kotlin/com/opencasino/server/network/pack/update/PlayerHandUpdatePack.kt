package com.opencasino.server.network.pack.update

import com.opencasino.server.game.model.Card
import com.opencasino.server.network.pack.PublicUpdatePack
import com.opencasino.server.network.pack.UpdatePack


class PlayerHandUpdatePack(
    val player: PublicUpdatePack,
    val cards: List<Card?>,
): UpdatePack
