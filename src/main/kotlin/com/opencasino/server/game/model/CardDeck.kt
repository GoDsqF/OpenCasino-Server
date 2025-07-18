package com.opencasino.server.game.model

class CardDeck(){

    private var cards: MutableList<Card> = mutableListOf()

    constructor(stacks: Int) : this() {
        repeat(stacks) {
            for (rank in Card.Rank.entries) {
                for (suit in Card.Suit.entries) {
                    cards.add(Card(rank, suit))
                }
            }
        }
        cards.shuffleDeck()
    }

    fun dealCard(to: CardDeck, visibility: Boolean = true) = to.addCard(cards.removeFirst(), visibility)

    fun dealCards(count: Int, to: CardDeck) {
        for (i in 1..count) {
            to.addCard(cards[i])
            cards.removeAt(i)
        }
    }

    private fun CardDeck.addCard(card: Card, visibility: Boolean = true) {
        cards.add(Card(card.rank, card.suit, visibility))
    }

    fun getCards(): List<Card> {
        return cards
    }

    fun clear() {
        cards.clear()
    }

    fun openCards() {
        for (card in cards) {
            card.visible = true
        }
    }

    private fun MutableList<Card>.shuffleDeck() = cards.shuffle()
}