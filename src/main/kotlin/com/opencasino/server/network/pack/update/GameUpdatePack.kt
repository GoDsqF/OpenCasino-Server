package com.opencasino.server.network.pack.update

import com.opencasino.server.network.pack.UpdatePack
import com.opencasino.server.network.pack.blackjack.shared.DealerUpdatePack

data class GameUpdatePack(
    val player: BlackjackPrivatePlayerUpdatePack,
    val players: Collection<PlayerHandUpdatePack>,
    val dealer: DealerUpdatePack
): UpdatePack