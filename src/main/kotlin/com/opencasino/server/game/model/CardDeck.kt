package com.opencasino.server.game.model


class CardDeck(){

    private var cards: MutableList<Card> = mutableListOf()
    private var visibilities: MutableList<Boolean> = mutableListOf()

    constructor(stacks: Int) : this() {
        repeat(stacks) {
            for (rank in Rank.entries) {
                for (suit in Suit.entries) {
                    cards.add(Card(rank, suit))
                    visibilities.add(false)
                }
            }
        }
        shuffleDeck()
    }

    fun dealCard(to: CardDeck, visibility: Boolean = true) {
        val card = cards.removeFirst()
        visibilities.removeFirst()
        to.addCard(card, visibility)
    }

    fun dealCards(count: Int, to: CardDeck) {
        for (i in 1..count) {
            to.addCard(cards[i])
            cards.removeAt(i)
            visibilities.removeAt(i)
        }
    }

    fun addCard(card: Card, visibility: Boolean = true) {
        cards.add(card)
        visibilities.add(visibility)
    }

    fun getCards(): List<Card> {
        return cards
    }

    fun isVisible(index: Int): Boolean = visibilities[index]

    fun clear() {
        cards.clear()
        visibilities.clear()
    }

    fun openCards() {
        for (i in visibilities.indices) {
            visibilities[i] = true
        }
    }

    fun toPublicView(): List<Card?> = cards.mapIndexed { i, card -> if (visibilities[i]) card else null }

    private fun shuffleDeck() {
        val pairs = cards.indices.map { i -> cards[i] to visibilities[i] }.toMutableList()
        pairs.shuffle()
        cards.clear()
        visibilities.clear()
        pairs.forEach { (c, v) ->
            cards.add(c)
            visibilities.add(v)
        }
    }
}