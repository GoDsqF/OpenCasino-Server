package com.opencasino.server.network.pack.poker.shared

import com.opencasino.server.game.model.Card

data class deprecatedDealerUpdatePack(
    val cards: List<Card?>
)