package com.opencasino.server.game.model

class CardDeck(stacks: Int) {

    private val deck: MutableList<Card> = generateDeck(stacks)

    private fun generateDeck(packs: Int): MutableList<Card> {
        val result = mutableListOf<Card>()
        repeat(packs) {
            for (rank in Card.Rank.entries) {
                for (suit in Card.Suit.entries) {
                    result.add(Card(rank, suit))
                }
            }
        }
        return result
    }

    private fun MutableList<Card>.shuffleDeck() = this.shuffle()

    fun MutableList<Card>.dealCard(visibility: Boolean = true) {
        this.add(deck[0])
        deck.removeFirst()
    }
    /**
     * Generic card class for type safety
     */
}