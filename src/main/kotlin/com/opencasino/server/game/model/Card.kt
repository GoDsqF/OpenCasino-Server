package com.opencasino.server.game.model

data class Card(
    val rank: Rank,
    val suit: Suit
) {
    override fun toString(): String = "${this.rank} of ${this.suit}"
}