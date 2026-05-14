package com.opencasino.server.network.pack.poker.update

import com.opencasino.server.network.pack.UpdatePack
import com.opencasino.server.network.pack.shared.DealerUpdatePack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack
import com.opencasino.server.service.shared.PokerPhase

data class GameUpdatePack(
    val player: PrivatePlayerUpdatePack,
    val players: Collection<PlayerHandUpdatePack>,
    val dealer: DealerUpdatePack,
    val phase: PokerPhase,
    val currentPosition: Int?,
    val pot: Double,
    val lastMaxBet: Double
): UpdatePack