package com.opencasino.server.game.blackjack

import com.opencasino.server.game.blackjack.model.BlackjackCondition
import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.model.Rank
import com.opencasino.server.game.model.Suit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Тестирует логику дилера и определение результатов,
 * воспроизводя алгоритм из BlackjackGameRoom.
 */
class BlackjackDealerLogicTest {

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

    /** Воспроизводит initialCheck из BlackjackGameRoom */
    private fun initialCheck(dealerHand: CardDeck, playerHand: CardDeck): BlackjackCondition? {
        val dealerSum = calculateScore(dealerHand)
        val playerSum = calculateScore(playerHand)
        return when {
            dealerSum == 21 && playerSum != 21 -> BlackjackCondition.DealerBlackjack
            playerSum == 21 -> BlackjackCondition.PlayerWinBlackjack
            else -> null
        }
    }

    /** Воспроизводит onPlayerTurn из BlackjackGameRoom */
    private fun checkPlayerTurn(playerHand: CardDeck): BlackjackCondition? {
        val playerSum = calculateScore(playerHand)
        return when {
            playerSum > 21 -> BlackjackCondition.DealerWin
            else -> null
        }
    }

/** Воспроизводит onDealerTurn (рекурсивную) из BlackjackGameRoom */
private fun dealerPlay(dealerHand: CardDeck, playerHand: CardDeck, sourceDeck: CardDeck): BlackjackCondition? {
    val dealerSum = calculateScore(dealerHand)
    val playerSum = calculateScore(playerHand)

    if (dealerSum < 17) {
        sourceDeck.dealCard(dealerHand)
        return dealerPlay(dealerHand, playerHand, sourceDeck)
    }
    return when {
        dealerSum > 21 -> BlackjackCondition.PlayerWin
        dealerSum < playerSum -> BlackjackCondition.PlayerWin
        dealerSum > playerSum -> BlackjackCondition.DealerWin
        else -> BlackjackCondition.Draw
    }
}

    // =========================================================================
    // Initial Check
    // =========================================================================

    @Nested
    inner class InitialCheckTests {

        @Test
        fun `dealer blackjack detected`() {
            val dealer = deckOf(Rank.CA to Suit.SPADES, Rank.CK to Suit.HEARTS)
            val player = deckOf(Rank.C9 to Suit.DIAMONDS, Rank.C8 to Suit.CLUBS)
            assertEquals(BlackjackCondition.DealerBlackjack, initialCheck(dealer, player))
        }

        @Test
        fun `player blackjack detected`() {
            val dealer = deckOf(Rank.C9 to Suit.DIAMONDS, Rank.C8 to Suit.CLUBS)
            val player = deckOf(Rank.CA to Suit.SPADES, Rank.CQ to Suit.HEARTS)
            assertEquals(BlackjackCondition.PlayerWinBlackjack, initialCheck(dealer, player))
        }

        @Test
        fun `both blackjack - player wins`() {
            // В текущей реализации: if dealer==21 && player!=21 -> DealerBJ, elif player==21 -> PlayerBJ
            // Если оба 21, playerSum==21 попадает во вторую ветку
            val dealer = deckOf(Rank.CA to Suit.SPADES, Rank.CK to Suit.HEARTS)
            val player = deckOf(Rank.CA to Suit.DIAMONDS, Rank.CQ to Suit.CLUBS)
            assertEquals(BlackjackCondition.PlayerWinBlackjack, initialCheck(dealer, player))
        }

        @Test
        fun `no blackjack returns null`() {
            val dealer = deckOf(Rank.C9 to Suit.SPADES, Rank.C7 to Suit.HEARTS)
            val player = deckOf(Rank.C8 to Suit.DIAMONDS, Rank.C6 to Suit.CLUBS)
            assertNull(initialCheck(dealer, player))
        }

        @Test
        fun `dealer 20 player 20 returns null`() {
            val dealer = deckOf(Rank.CK to Suit.SPADES, Rank.CQ to Suit.HEARTS)
            val player = deckOf(Rank.CJ to Suit.DIAMONDS, Rank.C10 to Suit.CLUBS)
            assertNull(initialCheck(dealer, player))
        }

        @Test
        fun `dealer low cards player low cards returns null`() {
            val dealer = deckOf(Rank.C2 to Suit.SPADES, Rank.C3 to Suit.HEARTS)
            val player = deckOf(Rank.C4 to Suit.DIAMONDS, Rank.C5 to Suit.CLUBS)
            assertNull(initialCheck(dealer, player))
        }
    }

    // =========================================================================
    // Player Turn
    // =========================================================================

    @Nested
    inner class PlayerTurnTests {

        @Test
        fun `player busts over 21`() {
            val player = deckOf(
                Rank.CK to Suit.SPADES,
                Rank.CQ to Suit.HEARTS,
                Rank.C5 to Suit.DIAMONDS
            )
            assertEquals(BlackjackCondition.DealerWin, checkPlayerTurn(player))
        }

        @Test
        fun `player at exactly 21 does not bust`() {
            val player = deckOf(
                Rank.C7 to Suit.SPADES,
                Rank.C4 to Suit.HEARTS,
                Rank.CK to Suit.DIAMONDS
            )
            assertNull(checkPlayerTurn(player))
        }

        @Test
        fun `player at 20 does not bust`() {
            val player = deckOf(Rank.CK to Suit.SPADES, Rank.CQ to Suit.HEARTS)
            assertNull(checkPlayerTurn(player))
        }

        @Test
        fun `player at 22 busts`() {
            val player = deckOf(
                Rank.CK to Suit.SPADES,
                Rank.CQ to Suit.HEARTS,
                Rank.C2 to Suit.DIAMONDS
            )
            assertEquals(BlackjackCondition.DealerWin, checkPlayerTurn(player))
        }

        @Test
        fun `player with ace soft hand does not bust`() {
            val player = deckOf(
                Rank.CA to Suit.SPADES,
                Rank.C8 to Suit.HEARTS,
                Rank.C7 to Suit.DIAMONDS
            )
            // 11+8+7=26 -> ace demotes -> 1+8+7=16
            assertNull(checkPlayerTurn(player))
        }

        @Test
        fun `player four cards bust`() {
            val player = deckOf(
                Rank.C8 to Suit.SPADES,
                Rank.C7 to Suit.HEARTS,
                Rank.C4 to Suit.DIAMONDS,
                Rank.C5 to Suit.CLUBS
            )
            // 8+7+4+5=24
            assertEquals(BlackjackCondition.DealerWin, checkPlayerTurn(player))
        }

        @Test
        fun `player four cards no bust`() {
            val player = deckOf(
                Rank.C2 to Suit.SPADES,
                Rank.C3 to Suit.HEARTS,
                Rank.C4 to Suit.DIAMONDS,
                Rank.C5 to Suit.CLUBS
            )
            // 2+3+4+5=14
            assertNull(checkPlayerTurn(player))
        }

        @Test
        fun `minimum hand value 4`() {
            val player = deckOf(Rank.C2 to Suit.SPADES, Rank.C2 to Suit.HEARTS)
            assertNull(checkPlayerTurn(player))
        }
    }

    // =========================================================================
    // Dealer Turn (полный розыгрыш)
    // =========================================================================

    @Nested
    inner class DealerTurnTests {

        private fun sourceDeckWith(vararg cards: Pair<Rank, Suit>): CardDeck {
            val deck = CardDeck()
            cards.forEach { deck.addCard(Card(it.first, it.second)) }
            return deck
        }

        @Test
        fun `dealer stands on 17 - dealer wins with 19 vs 18`() {
            val dealer = deckOf(Rank.CK to Suit.SPADES, Rank.C9 to Suit.HEARTS)
            val player = deckOf(Rank.CK to Suit.DIAMONDS, Rank.C8 to Suit.CLUBS)
            val source = sourceDeckWith() // не понадобится — dealer уже >= 17
            assertEquals(BlackjackCondition.DealerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `dealer stands on 17 - player wins with 20 vs 17`() {
            val dealer = deckOf(Rank.CK to Suit.SPADES, Rank.C7 to Suit.HEARTS)
            val player = deckOf(Rank.CK to Suit.DIAMONDS, Rank.CQ to Suit.CLUBS)
            val source = sourceDeckWith()
            assertEquals(BlackjackCondition.PlayerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `draw when both have same score`() {
            val dealer = deckOf(Rank.CK to Suit.SPADES, Rank.C8 to Suit.HEARTS)
            val player = deckOf(Rank.CQ to Suit.DIAMONDS, Rank.C8 to Suit.CLUBS)
            val source = sourceDeckWith()
            assertEquals(BlackjackCondition.Draw, dealerPlay(dealer, player, source))
        }

        @Test
        fun `dealer hits on 16 and busts`() {
            val dealer = deckOf(Rank.CK to Suit.SPADES, Rank.C6 to Suit.HEARTS)
            val player = deckOf(Rank.C9 to Suit.DIAMONDS, Rank.C9 to Suit.CLUBS)
            // dealer=16, hits, gets K -> 16+10=26 bust
            val source = sourceDeckWith(Rank.CK to Suit.DIAMONDS)
            assertEquals(BlackjackCondition.PlayerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `dealer hits on 16 and gets 17`() {
            val dealer = deckOf(Rank.CK to Suit.SPADES, Rank.C6 to Suit.HEARTS)
            val player = deckOf(Rank.C9 to Suit.DIAMONDS, Rank.C9 to Suit.CLUBS)
            // dealer=16, hits, gets A -> 16+11=27 -> ace demotes -> 16+1=17
            // 17 < 18 -> PlayerWin
            val source = sourceDeckWith(Rank.CA to Suit.DIAMONDS)
            assertEquals(BlackjackCondition.PlayerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `dealer hits on 16 gets to 20 wins vs 18`() {
            val dealer = deckOf(Rank.CK to Suit.SPADES, Rank.C6 to Suit.HEARTS)
            val player = deckOf(Rank.CK to Suit.DIAMONDS, Rank.C8 to Suit.CLUBS)
            // dealer=16, hits, gets 4 -> 20
            // 20 > 18 -> DealerWin
            val source = sourceDeckWith(Rank.C4 to Suit.DIAMONDS)
            assertEquals(BlackjackCondition.DealerWin, dealerPlay(dealer, player, source))
        }
        @Test
        fun `dealer hits multiple times before standing`() {
            val dealer = deckOf(Rank.C2 to Suit.SPADES, Rank.C3 to Suit.HEARTS)
            val player = deckOf(Rank.CK to Suit.DIAMONDS, Rank.C7 to Suit.CLUBS) // 17
            // dealer=5, hits 4 -> 9, hits 3 -> 12, hits 6 -> 18
            // 18 > 17 -> DealerWin
            val source = sourceDeckWith(
                Rank.C4 to Suit.DIAMONDS,
                Rank.C3 to Suit.CLUBS,
                Rank.C6 to Suit.SPADES
            )
            assertEquals(BlackjackCondition.DealerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `dealer hits multiple times and busts`() {
            val dealer = deckOf(Rank.C2 to Suit.SPADES, Rank.C3 to Suit.HEARTS)
            val player = deckOf(Rank.CK to Suit.DIAMONDS, Rank.C8 to Suit.CLUBS) // 18
            // dealer=5, hits 5 -> 10, hits 4 -> 14, hits 9 -> 23 bust
            val source = sourceDeckWith(
                Rank.C5 to Suit.DIAMONDS,
                Rank.C4 to Suit.CLUBS,
                Rank.C9 to Suit.SPADES
            )
            assertEquals(BlackjackCondition.PlayerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `dealer soft 17 with ace stands`() {
            val dealer = deckOf(Rank.CA to Suit.SPADES, Rank.C6 to Suit.HEARTS)
            val player = deckOf(Rank.CK to Suit.DIAMONDS, Rank.C8 to Suit.CLUBS) // 18
            // dealer: 11+6=17, stands
            // 17 < 18 -> PlayerWin
            val source = sourceDeckWith()
            assertEquals(BlackjackCondition.PlayerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `dealer 21 vs player 20 dealer wins`() {
            val dealer = deckOf(
                Rank.C7 to Suit.SPADES,
                Rank.C4 to Suit.HEARTS,
                Rank.CK to Suit.DIAMONDS
            )
            val player = deckOf(Rank.CK to Suit.CLUBS, Rank.CQ to Suit.SPADES) // 20
            val source = sourceDeckWith()
            assertEquals(BlackjackCondition.DealerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `dealer 21 vs player 21 is draw`() {
            val dealer = deckOf(
                Rank.C7 to Suit.SPADES,
                Rank.C4 to Suit.HEARTS,
                Rank.CK to Suit.DIAMONDS
            )
            val player = deckOf(
                Rank.C6 to Suit.CLUBS,
                Rank.C5 to Suit.SPADES,
                Rank.CQ to Suit.HEARTS
            )
            val source = sourceDeckWith()
            assertEquals(BlackjackCondition.Draw, dealerPlay(dealer, player, source))
        }

        @Test
        fun `dealer exactly 17 vs player 17 is draw`() {
            val dealer = deckOf(Rank.CK to Suit.SPADES, Rank.C7 to Suit.HEARTS)
            val player = deckOf(Rank.CQ to Suit.DIAMONDS, Rank.C7 to Suit.CLUBS)
            val source = sourceDeckWith()
            assertEquals(BlackjackCondition.Draw, dealerPlay(dealer, player, source))
        }

        @Test
        fun `dealer hits from 12 wins with exact 21`() {
            val dealer = deckOf(Rank.C6 to Suit.SPADES, Rank.C6 to Suit.HEARTS)
            val player = deckOf(Rank.CK to Suit.DIAMONDS, Rank.CQ to Suit.CLUBS) // 20
            // dealer=12, hits 9 -> 21
            val source = sourceDeckWith(Rank.C9 to Suit.DIAMONDS)
            assertEquals(BlackjackCondition.DealerWin, dealerPlay(dealer, player, source))
        }
    }

    // =========================================================================
    // Полные сценарии раундов
    // =========================================================================

    @Nested
    inner class FullRoundScenarios {

        private fun sourceDeckWith(vararg cards: Pair<Rank, Suit>): CardDeck {
            val deck = CardDeck()
            cards.forEach { deck.addCard(Card(it.first, it.second)) }
            return deck
        }

        @Test
        fun `scenario - player gets blackjack immediately`() {
            val dealer = deckOf(Rank.C9 to Suit.SPADES, Rank.C7 to Suit.HEARTS)
            val player = deckOf(Rank.CA to Suit.DIAMONDS, Rank.CK to Suit.CLUBS)

            val result = initialCheck(dealer, player)
            assertEquals(BlackjackCondition.PlayerWinBlackjack, result)
            // Игра заканчивается сразу, dealer turn не нужен
        }

        @Test
        fun `scenario - dealer gets blackjack immediately`() {
            val dealer = deckOf(Rank.CA to Suit.SPADES, Rank.CQ to Suit.HEARTS)
            val player = deckOf(Rank.C9 to Suit.DIAMONDS, Rank.C8 to Suit.CLUBS)

            val result = initialCheck(dealer, player)
            assertEquals(BlackjackCondition.DealerBlackjack, result)
        }

        @Test
        fun `scenario - player hits once then stands, dealer plays`() {
            val dealer = deckOf(Rank.CK to Suit.SPADES, Rank.C6 to Suit.HEARTS) // 16
            val player = deckOf(Rank.C8 to Suit.DIAMONDS, Rank.C5 to Suit.CLUBS) // 13

            // initial check
            assertNull(initialCheck(dealer, player))

            // player hits: gets 6 -> 13+6=19
            player.addCard(Card(Rank.C6, Suit.DIAMONDS))
            assertNull(checkPlayerTurn(player)) // no bust

            // player stands -> dealer turn
            // dealer=16 < 17, hits Q -> 16+10=26 bust
            val source = sourceDeckWith(Rank.CQ to Suit.DIAMONDS)
            assertEquals(BlackjackCondition.PlayerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `scenario - player hits twice and busts`() {
            val player = deckOf(Rank.CK to Suit.SPADES, Rank.C5 to Suit.HEARTS) // 15

            assertNull(checkPlayerTurn(player)) // no bust yet

            // hit 1: gets 3 -> 18
            player.addCard(Card(Rank.C3, Suit.DIAMONDS))
            assertNull(checkPlayerTurn(player))

            // hit 2: gets 7 -> 25 bust
            player.addCard(Card(Rank.C7, Suit.CLUBS))
            assertEquals(BlackjackCondition.DealerWin, checkPlayerTurn(player))
        }

        @Test
        fun `scenario - player hits to 21 then dealer plays`() {
            val dealer = deckOf(Rank.C9 to Suit.SPADES, Rank.C8 to Suit.HEARTS) // 17
            val player = deckOf(Rank.C7 to Suit.DIAMONDS, Rank.C4 to Suit.CLUBS) // 11

            assertNull(initialCheck(dealer, player))

            // player hits: gets K -> 11+10=21
            player.addCard(Card(Rank.CK, Suit.HEARTS))
            assertNull(checkPlayerTurn(player))

            // dealer turn: 17, stands. 17 < 21 -> PlayerWin
            val source = sourceDeckWith()
            assertEquals(BlackjackCondition.PlayerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `scenario - close game ending in draw`() {
            val dealer = deckOf(Rank.C9 to Suit.SPADES, Rank.C3 to Suit.HEARTS) // 12
            val player = deckOf(Rank.CK to Suit.DIAMONDS, Rank.C8 to Suit.CLUBS) // 18

            assertNull(initialCheck(dealer, player))
            assertNull(checkPlayerTurn(player)) // player stands at 18

            // dealer: 12, hits 6 -> 18. 18==18 -> Draw
            val source = sourceDeckWith(Rank.C6 to Suit.DIAMONDS)
            assertEquals(BlackjackCondition.Draw, dealerPlay(dealer, player, source))
        }

        @Test
        fun `scenario - low cards long game`() {
            val dealer = deckOf(Rank.C2 to Suit.SPADES, Rank.C3 to Suit.HEARTS) // 5
            val player = deckOf(Rank.C4 to Suit.DIAMONDS, Rank.C3 to Suit.CLUBS) // 7

            assertNull(initialCheck(dealer, player))

            // player hits: 2 -> 9
            player.addCard(Card(Rank.C2, Suit.HEARTS))
            assertNull(checkPlayerTurn(player))

            // player hits: 5 -> 14
            player.addCard(Card(Rank.C5, Suit.SPADES))
            assertNull(checkPlayerTurn(player))

            // player hits: 6 -> 20
            player.addCard(Card(Rank.C6, Suit.HEARTS))
            assertNull(checkPlayerTurn(player))

            // player stands at 20
            // dealer: 5, hits 4->9, hits 3->12, hits 5->17, stands
            // 17 < 20 -> PlayerWin
            val source = sourceDeckWith(
                Rank.C4 to Suit.CLUBS,
                Rank.C3 to Suit.DIAMONDS,
                Rank.C5 to Suit.HEARTS
            )
            assertEquals(BlackjackCondition.PlayerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `scenario - ace heavy game with multiple demotions`() {
            val dealer = deckOf(Rank.CA to Suit.SPADES, Rank.C5 to Suit.HEARTS) // 16 (soft)
            val player = deckOf(Rank.CA to Suit.DIAMONDS, Rank.CA to Suit.CLUBS) // 12 (one demoted)

            assertNull(initialCheck(dealer, player))

            // player hits: 8 -> 12+8=20
            player.addCard(Card(Rank.C8, Suit.HEARTS))
            assertNull(checkPlayerTurn(player))

            // player stands at 20
            // dealer: soft 16 (A+5), hits 5 -> 21
            val source = sourceDeckWith(Rank.C5 to Suit.DIAMONDS)
            assertEquals(BlackjackCondition.DealerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `scenario - dealer busts on first hit`() {
            val dealer = deckOf(Rank.CK to Suit.SPADES, Rank.C4 to Suit.HEARTS) // 14
            val player = deckOf(Rank.C9 to Suit.DIAMONDS, Rank.C9 to Suit.CLUBS) // 18

            assertNull(initialCheck(dealer, player))
            assertNull(checkPlayerTurn(player)) // player stands at 18

            // dealer: 14, hits K -> 24 bust
            val source = sourceDeckWith(Rank.CK to Suit.CLUBS)
            assertEquals(BlackjackCondition.PlayerWin, dealerPlay(dealer, player, source))
        }

        @Test
        fun `scenario - player barely survives with ace demotion`() {
            val player = deckOf(
                Rank.CA to Suit.SPADES,
                Rank.C6 to Suit.HEARTS
            ) // soft 17

            assertNull(checkPlayerTurn(player))

            // hit: K -> 11+6+10=27 -> ace demotes -> 1+6+10=17
            player.addCard(Card(Rank.CK, Suit.DIAMONDS))
            assertEquals(17, calculateScore(player))
            assertNull(checkPlayerTurn(player))

            // hit: 5 -> 17+5=22 bust (ace already demoted)
            player.addCard(Card(Rank.C5, Suit.CLUBS))
            assertEquals(22, calculateScore(player))
            assertEquals(BlackjackCondition.DealerWin, checkPlayerTurn(player))
        }
    }
}