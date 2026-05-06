package com.opencasino.server.game.blackjack.model

import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.model.Rank
import com.opencasino.server.game.model.Suit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Тестируем calculateScore — вынесем логику в хелпер,
 * т.к. в BlackjackGameRoom она private. Дублируем алгоритм
 * чтобы валидировать саму математику.
 */
class BlackjackScoreCalculationTest {

    private val combiner: Map<Rank, Int> = mapOf(
        Rank.C2 to 2, Rank.C3 to 3, Rank.C4 to 4, Rank.C5 to 5,
        Rank.C6 to 6, Rank.C7 to 7, Rank.C8 to 8, Rank.C9 to 9,
        Rank.C10 to 10, Rank.CJ to 10, Rank.CQ to 10, Rank.CK to 10,
        Rank.CA to 11
    )

    private fun calculateScore(hand: CardDeck): Int {
        val cards = hand.getCards()
        var score = cards.sumOf { combiner[it.rank]!! }
        var numAces = cards.count { it.rank == Rank.CA }
        while (score > 21 && numAces > 0) {
            score -= 10
            numAces--
        }
        return score
    }

    private fun deckOf(vararg cards: Pair<Rank, Suit>): CardDeck {
        val deck = CardDeck()
        cards.forEach { deck.addCard(Card(it.first, it.second)) }
        return deck
    }

    // =========================================================================
    // Базовые подсчёты
    // =========================================================================

    @Nested
    inner class BasicScoring {

        @Test
        fun `two number cards sum correctly`() {
            val hand = deckOf(Rank.C5 to Suit.HEARTS, Rank.C7 to Suit.SPADES)
            assertEquals(12, calculateScore(hand))
        }

        @Test
        fun `face cards count as 10`() {
            val hand = deckOf(Rank.CJ to Suit.HEARTS, Rank.CQ to Suit.SPADES)
            assertEquals(20, calculateScore(hand))
        }

        @Test
        fun `king counts as 10`() {
            val hand = deckOf(Rank.CK to Suit.DIAMONDS, Rank.C3 to Suit.CLUBS)
            assertEquals(13, calculateScore(hand))
        }

        @Test
        fun `10 counts as 10`() {
            val hand = deckOf(Rank.C10 to Suit.HEARTS, Rank.C10 to Suit.SPADES)
            assertEquals(20, calculateScore(hand))
        }

        @Test
        fun `three cards sum correctly`() {
            val hand = deckOf(
                Rank.C3 to Suit.HEARTS,
                Rank.C4 to Suit.SPADES,
                Rank.C5 to Suit.DIAMONDS
            )
            assertEquals(12, calculateScore(hand))
        }

        @Test
        fun `all twos sum to 8`() {
            val hand = deckOf(
                Rank.C2 to Suit.HEARTS, Rank.C2 to Suit.SPADES,
                Rank.C2 to Suit.DIAMONDS, Rank.C2 to Suit.CLUBS
            )
            assertEquals(8, calculateScore(hand))
        }
    }

    // =========================================================================
    // Логика тузов
    // =========================================================================

    @Nested
    inner class AceLogic {

        @Test
        fun `single ace counts as 11 when safe`() {
            val hand = deckOf(Rank.CA to Suit.HEARTS, Rank.C5 to Suit.SPADES)
            assertEquals(16, calculateScore(hand))
        }

        @Test
        fun `ace plus face card is blackjack 21`() {
            val hand = deckOf(Rank.CA to Suit.HEARTS, Rank.CK to Suit.SPADES)
            assertEquals(21, calculateScore(hand))
        }

        @Test
        fun `ace plus 10 is blackjack 21`() {
            val hand = deckOf(Rank.CA to Suit.HEARTS, Rank.C10 to Suit.SPADES)
            assertEquals(21, calculateScore(hand))
        }

        @Test
        fun `ace demotes to 1 when would bust`() {
            val hand = deckOf(
                Rank.CA to Suit.HEARTS,
                Rank.C8 to Suit.SPADES,
                Rank.C7 to Suit.DIAMONDS
            )
            // 11+8+7=26 -> bust, ace becomes 1 -> 1+8+7=16
            assertEquals(16, calculateScore(hand))
        }

        @Test
        fun `two aces - one demotes to avoid bust`() {
            val hand = deckOf(Rank.CA to Suit.HEARTS, Rank.CA to Suit.SPADES)
            // 11+11=22 -> one demotes -> 11+1=12
            assertEquals(12, calculateScore(hand))
        }

        @Test
        fun `two aces plus 9 equals 21`() {
            val hand = deckOf(
                Rank.CA to Suit.HEARTS,
                Rank.CA to Suit.SPADES,
                Rank.C9 to Suit.DIAMONDS
            )
            // 11+11+9=31 -> 1+11+9=21
            assertEquals(21, calculateScore(hand))
        }

        @Test
        fun `three aces equal 13`() {
            val hand = deckOf(
                Rank.CA to Suit.HEARTS,
                Rank.CA to Suit.SPADES,
                Rank.CA to Suit.DIAMONDS
            )
            // 11+11+11=33 -> 1+11+11=23 -> 1+1+11=13
            assertEquals(13, calculateScore(hand))
        }

        @Test
        fun `four aces equal 14`() {
            val hand = deckOf(
                Rank.CA to Suit.HEARTS, Rank.CA to Suit.SPADES,
                Rank.CA to Suit.DIAMONDS, Rank.CA to Suit.CLUBS
            )
            // 44 -> 34 -> 24 -> 14
            assertEquals(14, calculateScore(hand))
        }

        @Test
        fun `ace with high cards demotes correctly`() {
            val hand = deckOf(
                Rank.CA to Suit.HEARTS,
                Rank.CK to Suit.SPADES,
                Rank.CQ to Suit.DIAMONDS
            )
            // 11+10+10=31 -> 1+10+10=21
            assertEquals(21, calculateScore(hand))
        }

        @Test
        fun `ace demotes and still busts when impossible`() {
            val hand = deckOf(
                Rank.CA to Suit.HEARTS,
                Rank.CK to Suit.SPADES,
                Rank.CQ to Suit.DIAMONDS,
                Rank.C5 to Suit.CLUBS
            )
            // 11+10+10+5=36 -> 1+10+10+5=26 (bust)
            assertEquals(26, calculateScore(hand))
        }
    }

    // =========================================================================
    // Граничные значения
    // =========================================================================

    @Nested
    inner class EdgeCases {

        @Test
        fun `exact 21 without ace`() {
            val hand = deckOf(
                Rank.C7 to Suit.HEARTS,
                Rank.C4 to Suit.SPADES,
                Rank.CK to Suit.DIAMONDS
            )
            assertEquals(21, calculateScore(hand))
        }

        @Test
        fun `bust at 22`() {
            val hand = deckOf(
                Rank.CK to Suit.HEARTS,
                Rank.CQ to Suit.SPADES,
                Rank.C2 to Suit.DIAMONDS
            )
            assertEquals(22, calculateScore(hand))
        }

        @Test
        fun `minimum possible hand`() {
            val hand = deckOf(Rank.C2 to Suit.HEARTS, Rank.C2 to Suit.SPADES)
            assertEquals(4, calculateScore(hand))
        }

        @Test
        fun `maximum face card hand`() {
            val hand = deckOf(
                Rank.CK to Suit.HEARTS, Rank.CK to Suit.SPADES,
                Rank.CK to Suit.DIAMONDS, Rank.CK to Suit.CLUBS
            )
            assertEquals(40, calculateScore(hand))
        }

        @Test
        fun `single card ace is 11`() {
            val hand = deckOf(Rank.CA to Suit.HEARTS)
            assertEquals(11, calculateScore(hand))
        }

        @Test
        fun `single card two is 2`() {
            val hand = deckOf(Rank.C2 to Suit.HEARTS)
            assertEquals(2, calculateScore(hand))
        }

        @Test
        fun `five card charlie - 5 cards under 21`() {
            val hand = deckOf(
                Rank.C2 to Suit.HEARTS, Rank.C3 to Suit.SPADES,
                Rank.C4 to Suit.DIAMONDS, Rank.C2 to Suit.CLUBS,
                Rank.C3 to Suit.HEARTS
            )
            assertEquals(14, calculateScore(hand))
        }
    }

    // =========================================================================
    // Определение блэкджека / условий
    // =========================================================================

    @Nested
    inner class BlackjackConditions {

        @Test
        fun `natural blackjack is exactly 21 with 2 cards`() {
            val hand = deckOf(Rank.CA to Suit.HEARTS, Rank.CK to Suit.SPADES)
            assertEquals(21, calculateScore(hand))
            assertEquals(2, hand.getCards().size)
        }

        @Test
        fun `21 with 3 cards is not natural blackjack`() {
            val hand = deckOf(
                Rank.C7 to Suit.HEARTS,
                Rank.C4 to Suit.SPADES,
                Rank.CK to Suit.DIAMONDS
            )
            assertEquals(21, calculateScore(hand))
            assertNotEquals(2, hand.getCards().size)
        }

        @Test
        fun `dealer stands on 17`() {
            val hand = deckOf(Rank.CK to Suit.HEARTS, Rank.C7 to Suit.SPADES)
            val score = calculateScore(hand)
            assertTrue(score >= 17)
        }

        @Test
        fun `dealer must hit on 16`() {
            val hand = deckOf(Rank.CK to Suit.HEARTS, Rank.C6 to Suit.SPADES)
            val score = calculateScore(hand)
            assertTrue(score < 17)
        }

        @Test
        fun `soft 17 with ace - ace counts as 11`() {
            val hand = deckOf(Rank.CA to Suit.HEARTS, Rank.C6 to Suit.SPADES)
            assertEquals(17, calculateScore(hand))
        }
    }
}