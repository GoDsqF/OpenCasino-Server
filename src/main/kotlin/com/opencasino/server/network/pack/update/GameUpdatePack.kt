package com.opencasino.server.network.pack.update

import com.opencasino.server.network.pack.UpdatePack

data class GameUpdatePack(
    val player: BlackjackPrivatePlayerUpdatePack,
    val players: Collection<PlayerHandUpdatePack>
): UpdatePack