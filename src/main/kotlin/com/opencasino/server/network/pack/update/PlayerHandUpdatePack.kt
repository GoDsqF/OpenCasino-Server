package com.opencasino.server.network.pack.update

import com.opencasino.server.game.model.Card
import com.opencasino.server.network.pack.PrivateUpdatePack
import com.opencasino.server.network.pack.UpdatePack


class PlayerHandUpdatePack(
    val player: PrivateUpdatePack,
    val cards: List<Card?>,
): UpdatePack
