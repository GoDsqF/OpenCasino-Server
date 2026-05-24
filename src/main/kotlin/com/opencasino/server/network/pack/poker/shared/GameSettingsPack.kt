package com.opencasino.server.network.pack.poker.shared

import com.opencasino.server.network.pack.InitPack

data class GameSettingsPack(
    val roomId: String,
    val loopRate: Long,
    val betType: String,
    val smallBlind: Double,
    val bigBlind: Double,
    /** Фактический нижний порог buy-in для этого стола (проверяется в onBuyIn). */
    val minBuyIn: Double,
    /** Верхний потолок buy-in; null = без потолка, ограничен только балансом игрока. */
    val maxBuyIn: Double?,
) : InitPack
