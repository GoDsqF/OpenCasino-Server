package com.opencasino.server.network.pack.poker.update

import com.opencasino.server.network.pack.UpdatePack
import com.opencasino.server.network.pack.shared.DealerUpdatePack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack

data class GameUpdatePack(
    val player: PrivatePlayerUpdatePack,
    val players: Collection<PlayerHandUpdatePack>,
    val dealer: DealerUpdatePack
): UpdatePack