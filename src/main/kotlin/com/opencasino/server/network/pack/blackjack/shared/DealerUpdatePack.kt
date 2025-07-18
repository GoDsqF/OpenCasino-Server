package com.opencasino.server.network.pack.blackjack.shared

import com.opencasino.server.game.model.Card

data class DealerUpdatePack(
    val cards: List<Card?>
)