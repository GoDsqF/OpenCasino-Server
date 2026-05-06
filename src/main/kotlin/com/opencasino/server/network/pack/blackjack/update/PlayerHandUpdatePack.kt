package com.opencasino.server.network.pack.blackjack.update

import com.opencasino.server.game.model.Card
import com.opencasino.server.game.poker.holdem.model.PokerHand
import com.opencasino.server.network.pack.UpdatePack
import com.opencasino.server.network.pack.blackjack.update.PrivatePlayerUpdatePack


abstract class BlackjackPlayerHandUpdatePack(
    val player: PrivatePlayerUpdatePack,
    val stack: Double,
    val cards: List<Card?>,
    val hand: String
): UpdatePack
