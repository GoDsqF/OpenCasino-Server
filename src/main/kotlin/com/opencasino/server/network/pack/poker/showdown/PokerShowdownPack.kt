package com.opencasino.server.network.pack.poker.showdown

import com.opencasino.server.game.model.Card
import com.opencasino.server.network.pack.Pack

data class PokerShowdownEntry(
    val id: Long,
    val payout: Double,
    val handCategory: String?,
    val handCards: List<Card>?,
    val holeCards: List<Card>?,
)

data class PokerShowdownSidePot(
    val amount: Double,
    val eligibleIds: List<Long>,
    val winnerIds: List<Long>,
)

data class PokerShowdownPack(
    val entries: List<PokerShowdownEntry>,
    val pots: List<PokerShowdownSidePot>,
) : Pack
