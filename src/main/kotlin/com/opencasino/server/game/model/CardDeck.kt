package com.opencasino.server.game.model

import java.security.SecureRandom

class CardDeck() {
    private var cards: MutableList<Card> = mutableListOf()
    private var visibilities: MutableList<Boolean> = mutableListOf()

    // shuffle=false строит упорядоченную колоду без перетасовки — комнаты затем
    // применяют provably-fair перестановку через [applyShuffle] (CRASH.md §1.5, R5).
    constructor(stacks: Int, shuffle: Boolean = true) : this() {
        repeat(stacks) {
            for (rank in Rank.entries) {
                for (suit in Suit.entries) {
                    cards.add(Card(rank, suit))
                    visibilities.add(false)
                }
            }
        }
        if (shuffle) shuffleDeck()
    }

    fun dealCard(
        to: CardDeck,
        visibility: Boolean = true,
    ) {
        val card = cards.removeFirst()
        visibilities.removeFirst()
        to.addCard(card, visibility)
    }

    fun dealCards(
        count: Int,
        to: CardDeck,
    ) {
        repeat(count) {
            val card = cards.removeFirst()
            visibilities.removeFirst()
            to.addCard(card)
        }
    }

    fun removeAt(index: Int): Card {
        val card = cards.removeAt(index)
        visibilities.removeAt(index)
        return card
    }

    fun addCard(
        card: Card,
        visibility: Boolean = true,
    ) {
        cards.add(card)
        visibilities.add(visibility)
    }

    fun getCards(): List<Card> = cards

    fun size(): Int = cards.size

    /**
     * Переставляет колоду по [permutation] (`result[i] = old[permutation[i]]`).
     * `permutation` — выход [com.opencasino.server.rng.ShuffleOutcomeProvider]:
     * детерминированная перестановка из committed seed → раздача проверяема клиентом
     * (provably-fair, R5). Размер обязан совпадать с текущей колодой.
     */
    fun applyShuffle(permutation: List<Int>) {
        require(permutation.size == cards.size) {
            "permutation size ${permutation.size} != deck size ${cards.size}"
        }
        val newCards = ArrayList<Card>(cards.size)
        val newVisibilities = ArrayList<Boolean>(cards.size)
        for (index in permutation) {
            newCards.add(cards[index])
            newVisibilities.add(visibilities[index])
        }
        cards = newCards
        visibilities = newVisibilities
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
        pairs.shuffle(secureRandom)
        cards.clear()
        visibilities.clear()
        pairs.forEach { (c, v) ->
            cards.add(c)
            visibilities.add(v)
        }
    }

    private companion object {
        // Fallback-перетасовка (не provably-fair путь) — крипто-стойкий источник
        // вместо дефолтного java.util.Random (CRASH.md §1.5).
        private val secureRandom = SecureRandom()
    }
}
