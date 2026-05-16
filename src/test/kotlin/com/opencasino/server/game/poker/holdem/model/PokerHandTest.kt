package com.opencasino.server.game.poker.holdem.model

import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.Rank
import com.opencasino.server.game.model.Suit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PokerHandTest {

    // =========================================================================
    // Определение комбинаций
    // =========================================================================

    @Nested
    inner class HandRecognition {

        @Test
        fun `recognizes straight flush`() {
            val hand = PokerHand.fromString("5H 6H 7H 8H 9H")
            assertTrue(hand.isStraightFlush)
            assertEquals("StraightFlush", hand.getHighestRank())
        }

        @Test
        fun `bestOf picks the strongest 5-card hand from 7`() {
            // Hole+community = KH KS KD KC 2H 7D 9C → four kings + 9 kicker
            val cards = "KH KS KD KC 2H 7D 9C".split(" ").map { s ->
                com.opencasino.server.game.model.Card(
                    com.opencasino.server.game.model.Rank.forCode(s[0]),
                    com.opencasino.server.game.model.Suit.forCode(s[1]),
                )
            }
            val best = PokerHand.bestOf(cards)
            assertEquals("FourOfAKind", best.getHighestRank())
        }

        @Test
        fun `bestOf returns the same hand when given exactly 5 cards`() {
            val hand = PokerHand.bestOf(PokerHand.fromString("KH KS KD KC 2H").cards)
            assertEquals("FourOfAKind", hand.getHighestRank())
        }

        @Test
        fun `bestOf prefers straight over pair from 7 cards`() {
            // 7 cards: 5H 6S 7D 8C 9H 9S 2D → straight 5-9 beats pair of nines
            val cards = "5H 6S 7D 8C 9H 9S 2D".split(" ").map { s ->
                com.opencasino.server.game.model.Card(
                    com.opencasino.server.game.model.Rank.forCode(s[0]),
                    com.opencasino.server.game.model.Suit.forCode(s[1]),
                )
            }
            val best = PokerHand.bestOf(cards)
            assertEquals("Straight", best.getHighestRank())
        }

        @Test
        fun `recognizes royal straight flush`() {
            val hand = PokerHand.fromString("TS JS QS KS AS")
            assertTrue(hand.isStraightFlush)
            assertTrue(hand.isFlush)
            assertTrue(hand.isStraight)
        }

        @Test
        fun `recognizes four of a kind`() {
            val hand = PokerHand.fromString("9H 9S 9D 9C 2H")
            assertTrue(hand.isFourOfAKind)
            assertEquals("FourOfAKind", hand.getHighestRank())
        }

        @Test
        fun `recognizes full house`() {
            val hand = PokerHand.fromString("3H 3S 3D 6C 6H")
            assertTrue(hand.isFullHouse)
            assertEquals("FullHouse", hand.getHighestRank())
        }

        @Test
        fun `recognizes flush`() {
            val hand = PokerHand.fromString("2H 5H 7H 9H AH")
            assertTrue(hand.isFlush)
            assertFalse(hand.isStraight)
            assertEquals("Flush", hand.getHighestRank())
        }

        @Test
        fun `recognizes straight`() {
            val hand = PokerHand.fromString("4H 5S 6D 7C 8H")
            assertTrue(hand.isStraight)
            assertFalse(hand.isFlush)
            assertEquals("Straight", hand.getHighestRank())
        }

        @Test
        fun `recognizes three of a kind`() {
            val hand = PokerHand.fromString("7H 7S 7D 2C 5H")
            assertTrue(hand.isThreeOfAKind)
            assertEquals("ThreeOfAKind", hand.getHighestRank())
        }

        @Test
        fun `recognizes two pair`() {
            val hand = PokerHand.fromString("4H 4S 8D 8C AH")
            assertTrue(hand.isTwoPair)
            assertEquals("TwoPair", hand.getHighestRank())
        }

        @Test
        fun `recognizes pair`() {
            val hand = PokerHand.fromString("JH JS 3D 7C AH")
            assertTrue(hand.isPair)
            assertFalse(hand.isTwoPair)
            assertEquals("Pair", hand.getHighestRank())
        }

        @Test
        fun `recognizes high card`() {
            val hand = PokerHand.fromString("2H 5S 7D 9C AH")
            assertFalse(hand.isPair)
            assertFalse(hand.isTwoPair)
            assertFalse(hand.isThreeOfAKind)
            assertFalse(hand.isStraight)
            assertFalse(hand.isFlush)
            assertFalse(hand.isFullHouse)
            assertFalse(hand.isFourOfAKind)
            assertFalse(hand.isStraightFlush)
            assertEquals("HighCard", hand.getHighestRank())
        }
    }
// =========================================================================
// Негативные свойства (комбинация НЕ является другой)
// =========================================================================

    @Nested
    inner class HandNegativeProperties {

        @Test
        fun `straight is not a flush`() {
            val hand = PokerHand.fromString("4H 5S 6D 7C 8H")
            assertTrue(hand.isStraight)
            assertFalse(hand.isFlush)
            assertFalse(hand.isStraightFlush)
        }

        @Test
        fun `flush is not a straight`() {
            val hand = PokerHand.fromString("2H 5H 7H 9H AH")
            assertTrue(hand.isFlush)
            assertFalse(hand.isStraight)
            assertFalse(hand.isStraightFlush)
        }

        @Test
        fun `full house is not four of a kind`() {
            val hand = PokerHand.fromString("3H 3S 3D 6C 6H")
            assertTrue(hand.isFullHouse)
            assertFalse(hand.isFourOfAKind)
        }

        @Test
        fun `three of a kind is not full house when remaining cards differ`() {
            val hand = PokerHand.fromString("7H 7S 7D 2C 5H")
            assertTrue(hand.isThreeOfAKind)
            assertFalse(hand.isFullHouse)
        }

        @Test
        fun `pair is not two pair`() {
            val hand = PokerHand.fromString("JH JS 3D 7C AH")
            assertTrue(hand.isPair)
            assertFalse(hand.isTwoPair)
            assertFalse(hand.isThreeOfAKind)
        }
    }

    // =========================================================================
    // Сравнение рук (compareTo)
    // =========================================================================

    @Nested
    inner class HandComparison {

        @Test
        fun `straight flush beats four of a kind`() {
            val straightFlush = PokerHand.fromString("5H 6H 7H 8H 9H")
            val fourOfAKind = PokerHand.fromString("9H 9S 9D 9C AH")
            assertEquals(PokerHand.WIN, straightFlush.compareTo(fourOfAKind))
            assertEquals(PokerHand.LOSS, fourOfAKind.compareTo(straightFlush))
        }

        @Test
        fun `four of a kind beats full house`() {
            val fourOfAKind = PokerHand.fromString("9H 9S 9D 9C 2H")
            val fullHouse = PokerHand.fromString("3H 3S 3D 6C 6H")
            assertEquals(PokerHand.WIN, fourOfAKind.compareTo(fullHouse))
            assertEquals(PokerHand.LOSS, fullHouse.compareTo(fourOfAKind))
        }

        @Test
        fun `full house beats flush`() {
            val fullHouse = PokerHand.fromString("3H 3S 3D 6C 6H")
            val flush = PokerHand.fromString("2H 5H 7H 9H AH")
            assertEquals(PokerHand.WIN, fullHouse.compareTo(flush))
            assertEquals(PokerHand.LOSS, flush.compareTo(fullHouse))
        }

        @Test
        fun `flush beats straight`() {
            val flush = PokerHand.fromString("2H 5H 7H 9H AH")
            val straight = PokerHand.fromString("4H 5S 6D 7C 8H")
            assertEquals(PokerHand.WIN, flush.compareTo(straight))
            assertEquals(PokerHand.LOSS, straight.compareTo(flush))
        }

        @Test
        fun `straight beats three of a kind`() {
            val straight = PokerHand.fromString("4H 5S 6D 7C 8H")
            val threeOfAKind = PokerHand.fromString("7H 7S 7D 2C 5S")
            assertEquals(PokerHand.WIN, straight.compareTo(threeOfAKind))
            assertEquals(PokerHand.LOSS, threeOfAKind.compareTo(straight))
        }

        @Test
        fun `three of a kind beats two pair`() {
            val threeOfAKind = PokerHand.fromString("7H 7S 7D 2C 5H")
            val twoPair = PokerHand.fromString("4H 4S 8D 8C AH")
            assertEquals(PokerHand.WIN, threeOfAKind.compareTo(twoPair))
            assertEquals(PokerHand.LOSS, twoPair.compareTo(threeOfAKind))
        }

        @Test
        fun `two pair beats pair`() {
            val twoPair = PokerHand.fromString("4H 4S 8D 8C AH")
            val pair = PokerHand.fromString("JH JS 3D 7C 2H")
            assertEquals(PokerHand.WIN, twoPair.compareTo(pair))
            assertEquals(PokerHand.LOSS, pair.compareTo(twoPair))
        }

        @Test
        fun `pair beats high card`() {
            val pair = PokerHand.fromString("JH JS 3D 7C 2H")
            val highCard = PokerHand.fromString("2H 5S 7D 9C AH")
            assertEquals(PokerHand.WIN, pair.compareTo(highCard))
            assertEquals(PokerHand.LOSS, highCard.compareTo(pair))
        }

        @Test
        fun `higher high card wins`() {
            val aceHigh = PokerHand.fromString("2H 4S 6D 8C AH")
            val kingHigh = PokerHand.fromString("2S 4D 6H 8S KS")
            assertEquals(PokerHand.WIN, aceHigh.compareTo(kingHigh))
            assertEquals(PokerHand.LOSS, kingHigh.compareTo(aceHigh))
        }

        @Test
        fun `higher straight wins`() {
            val highStraight = PokerHand.fromString("6H 7S 8D 9C TH")
            val lowStraight = PokerHand.fromString("4H 5S 6D 7C 8S")
            assertEquals(PokerHand.WIN, highStraight.compareTo(lowStraight))
            assertEquals(PokerHand.LOSS, lowStraight.compareTo(highStraight))
        }

        @Test
        fun `higher flush wins by high card`() {
            val aceFlush = PokerHand.fromString("2H 4H 6H 8H AH")
            val kingFlush = PokerHand.fromString("2S 4S 6S 8S KS")
            assertEquals(PokerHand.WIN, aceFlush.compareTo(kingFlush))
        }

        @Test
        fun `identical hands result in tie`() {
            val hand1 = PokerHand.fromString("2H 4S 6D 8C AH")
            val hand2 = PokerHand.fromString("2S 4D 6H 8S AS")
            assertEquals(PokerHand.TIE, hand1.compareTo(hand2))
        }

        @Test
        fun `same rank different kicker - second card decides`() {
            val hand1 = PokerHand.fromString("2H 4S 7D 9C AH")
            val hand2 = PokerHand.fromString("2S 4D 6H 9S AS")
            // Оба имеют A high, 9 second. hand1 имеет 7, hand2 имеет 6
            assertEquals(PokerHand.WIN, hand1.compareTo(hand2))
        }

        @Test
        fun `higher straight flush wins`() {
            val high = PokerHand.fromString("7H 8H 9H TH JH")
            val low = PokerHand.fromString("5S 6S 7S 8S 9S")
            assertEquals(PokerHand.WIN, high.compareTo(low))
            assertEquals(PokerHand.LOSS, low.compareTo(high))
        }

        @Test
        fun `same straight flush is tie`() {
            val hand1 = PokerHand.fromString("5H 6H 7H 8H 9H")
            val hand2 = PokerHand.fromString("5S 6S 7S 8S 9S")
            assertEquals(PokerHand.TIE, hand1.compareTo(hand2))
        }
    }

    // =========================================================================
    // Парсинг и валидация
    // =========================================================================

    @Nested
    inner class Parsing {

        @Test
        fun `fromString creates correct hand`() {
            val hand = PokerHand.fromString("2H 3H 4H 5H 6H")
            assertEquals(5, hand.cards.size)
            assertEquals(Rank.C2, hand.cards[0].rank)
            assertEquals(Suit.HEARTS, hand.cards[0].suit)
        }

        @Test
        fun `fromString throws on wrong number of cards`() {
            assertThrows(RuntimeException::class.java) {
                PokerHand.fromString("2H 3H 4H 5H")
            }
        }

        @Test
        fun `fromString throws on invalid card code`() {
            assertThrows(RuntimeException::class.java) {
                PokerHand.fromString("2H 3H 4H 5H ZZ")
            }
        }

        @Test
        fun `fromString throws on three character card code`() {
            assertThrows(RuntimeException::class.java) {
                PokerHand.fromString("2H 3H 4H 5H 10H")
            }
        }

        @Test
        fun `fromList creates sorted hand`() {
            val cards = listOf(
                Card(Rank.CA, Suit.SPADES),
                Card(Rank.C2, Suit.HEARTS),
                Card(Rank.CK, Suit.DIAMONDS),
                Card(Rank.C5, Suit.CLUBS),
                Card(Rank.C9, Suit.HEARTS)
            )
            val hand = PokerHand.fromList(cards)
            // Карты должны быть отсортированы по ordinal
            assertEquals(Rank.C2, hand.cards[0].rank)
            assertEquals(Rank.C5, hand.cards[1].rank)
            assertEquals(Rank.C9, hand.cards[2].rank)
            assertEquals(Rank.CK, hand.cards[3].rank)
            assertEquals(Rank.CA, hand.cards[4].rank)
        }
    }

    // =========================================================================
    // Полная иерархия — транзитивность
    // =========================================================================

    @Nested
    inner class FullHierarchy {

        @Test
        fun `complete hand ranking hierarchy is correct`() {
            val hands = listOf(
                PokerHand.fromString("2H 5S 7D 9C AH"),  // high card
                PokerHand.fromString("JH JS 3D 7C 2H"),  // pair
                PokerHand.fromString("4H 4S 8D 8C AH"),  // two pair
                PokerHand.fromString("7H 7S 7D 2C 5H"),  // three of a kind
                PokerHand.fromString("4H 5S 6D 7C 8H"),  // straight
                PokerHand.fromString("2D 5D 7D 9D AD"),   // flush
                PokerHand.fromString("3H 3S 3D 6C 6H"),  // full house
                PokerHand.fromString("9H 9S 9D 9C 2H"),  // four of a kind
                PokerHand.fromString("5H 6H 7H 8H 9H"),  // straight flush
            )

            for (i in hands.indices) {
                for (j in hands.indices) {
                    when {
                        i < j -> assertEquals(
                            PokerHand.LOSS, hands[i].compareTo(hands[j]),
                            "${hands[i].getHighestRank()} should lose to ${hands[j].getHighestRank()}"
                        )
                        i > j -> assertEquals(
                            PokerHand.WIN, hands[i].compareTo(hands[j]),
                            "${hands[i].getHighestRank()} should beat ${hands[j].getHighestRank()}"
                        )
                        else -> assertEquals(
                            PokerHand.TIE, hands[i].compareTo(hands[j]),
                            "${hands[i].getHighestRank()} should tie with itself"
                        )
                    }
                }
            }
        }
    }
}