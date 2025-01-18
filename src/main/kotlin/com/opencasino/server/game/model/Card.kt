package com.opencasino.server.game.model

class Card(
    val rank: Rank,
    val suit: Suit,
    var visible: Boolean = false
) {
    /**
     * Card suit enum
     */
    enum class Suit {
        Clubs,
        Diamonds,
        Hearts,
        Spades
    }

    /**
     * Card rank enum
     */
    enum class Rank {
        C2,
        C3,
        C4,
        C5,
        C6,
        C7,
        C8,
        C9,
        C10,
        Jack,
        Queen,
        King,
        Ace
    }

    val toString: (Card) -> String = { it ->
        "${it.rank} of ${it.suit}"
    }

    fun flipCards(hand: MutableList<Card>) {
        for (i in hand.indices) {
            hand[i] = Card(hand[i].rank, hand[i].suit, true)
        }
    }
}