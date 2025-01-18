package com.opencasino.server.network.pack.blackjack.update

import com.opencasino.server.network.pack.UpdatePack

data class GameUpdatePack(
    val player: BlackjackPrivatePlayerUpdatePack,
    val players: Collection<PlayerUpdatePack>
): UpdatePack