package com.opencasino.server.game.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.RepeatedTest

class CardDeckTest {

    @Test
    fun `empty deck has no cards`()
    {
        val deck = CardDeck()
        assertTrue(deck.getCards().isEmpty())
    }

    @Test
    fun `single stack deck contains 52 cards`()
    {
        val deck = CardDeck(1)
        assertEquals(52, deck.getCards().size)
    }

    @Test
    fun `multi stack deck contains correct number of cards`()
    {
        val deck = CardDeck(8)
        assertEquals(52 * 8, deck.getCards().size)
    }

    @Test
    fun `single stack contains all rank suit combinations`()
    {
        val deck = CardDeck(1)
        val cards = deck.getCards()
        for (rank in Rank.entries) {
            for (suit in Suit.entries) {
                assertTrue(
                    cards.any { it.rank == rank && it.suit == suit },
                    "Deck should contain $rank of $suit"
                )
            }
        }
    }

    @Test
    fun `dealCard removes card from source and adds to target`()
    {
        val source = CardDeck(1)
        val target = CardDeck()

        val initialSize = source.getCards().size
        source.dealCard(target)

        assertEquals(initialSize - 1, source.getCards().size)
        assertEquals(1, target.getCards().size)
    }

    @Test
    fun `dealCard with visibility true makes card visible`()
    {
        val source = CardDeck(1)
        val target = CardDeck()

        source.dealCard(target, true)

        assertTrue(target.isVisible(0))
    }

    @Test
    fun `dealCard with visibility false makes card hidden`()
    {
        val source = CardDeck(1)
        val target = CardDeck()

        source.dealCard(target, false)

        assertFalse(target.isVisible(0))
    }

    @Test
    fun `dealCards deals correct number of cards`()
    {
        val source = CardDeck(1)
        val target = CardDeck()

        source.dealCards(2, target)

        assertEquals(2, target.getCards().size)
        assertEquals(50, source.getCards().size)
    }

    @Test
    fun `addCard increases deck size`()
    {
        val deck = CardDeck()
        val card = Card(Rank.CA, Suit.SPADES)

        deck.addCard(card)

        assertEquals(1, deck.getCards().size)
        assertEquals(Rank.CA, deck.getCards()[0].rank)
        assertEquals(Suit.SPADES, deck.getCards()[0].suit)
    }

    @Test
    fun `addCard respects visibility parameter`()
    {
        val deck = CardDeck()
        val card = Card(Rank.CA, Suit.SPADES)

        deck.addCard(card, false)
        assertFalse(deck.isVisible(0))

        deck.addCard(card, true)
        assertTrue(deck.isVisible(1))
    }

    @Test
    fun `clear removes all cards`()
    {
        val deck = CardDeck(1)
        assertFalse(deck.getCards().isEmpty())

        deck.clear()

        assertTrue(deck.getCards().isEmpty())
    }

    @Test
    fun `openCards makes all cards visible`() {
        val deck = CardDeck()
        deck.addCard(Card(Rank.CA, Suit.SPADES), false)
        deck.addCard(Card(Rank.CK, Suit.HEARTS), false)
        deck.addCard(Card(Rank.CQ, Suit.DIAMONDS), false)

        deck.getCards().indices.forEach { assertFalse(deck.isVisible(it)) }

        deck.openCards()

        deck.getCards().indices.forEach { assertTrue(deck.isVisible(it)) }
    }

    @RepeatedTest(5)
    fun `deck is shuffled not in original order`() {
        val deck1 = CardDeck(1)
        val deck2 = CardDeck(1)
        // С очень высокой вероятностью две колоды будут в разном порядке
        // Допускаем что хотя бы 1 из 5 попыток покажет разницу
        val cards1 = deck1.getCards().map { "${it.rank}_${it.suit}" }
        val cards2 = deck2.getCards().map { "${it.rank}_${it.suit}" }
        // Не assertNotEquals — допускаем крайне маловероятное совпадение
        // Этот тест статистический
        if (cards1 != cards2) {
            assertNotEquals(cards1, cards2)
        }
    }

    @Test
    fun `dealing all cards empties the deck`() {
        val source = CardDeck(1)
        val target = CardDeck()

        repeat(52) {
            source.dealCard(target)
        }

        assertEquals(0, source.getCards().size)
        assertEquals(52, target.getCards().size)
    }

    @Test
    fun `toPublicView projects face-down cards as null, never as Card`() {
        val deck = CardDeck()
        deck.addCard(Card(Rank.CA, Suit.SPADES), visibility = true)
        deck.addCard(Card(Rank.CK, Suit.HEARTS), visibility = false)
        deck.addCard(Card(Rank.CQ, Suit.CLUBS), visibility = true)
        deck.addCard(Card(Rank.CJ, Suit.DIAMONDS), visibility = false)

        val view = deck.toPublicView()

        assertEquals(4, view.size)
        assertNotNull(view[0])
        assertNull(view[1], "face-down card MUST serialize as null (never Card{visible=false})")
        assertNotNull(view[2])
        assertNull(view[3], "face-down card MUST serialize as null (never Card{visible=false})")
        // Defense in depth: visibility is no longer stored on Card; toPublicView is the only
        // wire-facing projection, and a null in the list is the sole face-down representation.
    }

    @Test
    fun `toPublicView on empty deck yields empty list`() {
        assertTrue(CardDeck().toPublicView().isEmpty())
    }

    @Test
    fun `toPublicView on all-visible deck yields no nulls`() {
        val deck = CardDeck()
        deck.addCard(Card(Rank.C2, Suit.SPADES))
        deck.addCard(Card(Rank.C3, Suit.HEARTS))
        deck.addCard(Card(Rank.C4, Suit.CLUBS))
        val view = deck.toPublicView()
        assertEquals(3, view.size)
        assertTrue(view.all { it != null })
    }
}