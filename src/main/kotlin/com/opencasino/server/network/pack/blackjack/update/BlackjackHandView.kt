package com.opencasino.server.network.pack.blackjack.update

import com.opencasino.server.game.model.Card

data class BlackjackHandView(
    val cards: List<Card>,
    val bet: Double,
    val resolved: Boolean,
    val doubled: Boolean,
    val fromSplit: Boolean,
)
