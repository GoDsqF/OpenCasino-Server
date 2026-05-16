package com.opencasino.server.game.blackjack.model

import com.opencasino.server.game.model.CardDeck

class BlackjackHand(
    val deck: CardDeck = CardDeck(),
    var bet: Double = 0.0,
    var resolved: Boolean = false,
    var doubled: Boolean = false,
    var fromSplit: Boolean = false,
)
