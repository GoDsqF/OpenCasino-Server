package com.opencasino.server.game.poker.holdem.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PokerSidePotDistributionTest {

    private val royalFlush = PokerHand.fromString("TS JS QS KS AS")
    private val straightFlush = PokerHand.fromString("5H 6H 7H 8H 9H")
    private val fourKings = PokerHand.fromString("KH KS KD KC 2H")
    private val fullHouseAces = PokerHand.fromString("AH AS AD 6C 6H")
    private val fullHouseThrees = PokerHand.fromString("3H 3S 3D 6C 6H")
    private val pairTwos = PokerHand.fromString("2H 2S 5D 7C 9H")
    private val pairTwosAlt = PokerHand.fromString("2D 2C 5H 7D 9S")

    @Nested
    inner class SinglePot {

        @Test
        fun `one winner takes the whole pot`() {
            val result = PokerSidePotDistribution.distribute(
                listOf(
                    PokerContestant(1L, 100.0, royalFlush),
                    PokerContestant(2L, 100.0, fourKings),
                    PokerContestant(3L, 100.0, fullHouseThrees),
                )
            )
            assertEquals(300.0, result.payouts[1L])
            assertEquals(0.0, result.payouts[2L])
            assertEquals(0.0, result.payouts[3L])
            assertEquals(1, result.pots.size)
            assertEquals(listOf(1L), result.pots[0].winnerIds)
        }

        @Test
        fun `folded players still contribute but don't win`() {
            val result = PokerSidePotDistribution.distribute(
                listOf(
                    PokerContestant(1L, 50.0, null),
                    PokerContestant(2L, 50.0, fourKings),
                    PokerContestant(3L, 50.0, fullHouseThrees),
                )
            )
            assertEquals(0.0, result.payouts[1L])
            assertEquals(150.0, result.payouts[2L])
            assertEquals(0.0, result.payouts[3L])
        }
    }

    @Nested
    inner class TieDistribution {

        @Test
        fun `two-way tie splits evenly`() {
            val result = PokerSidePotDistribution.distribute(
                listOf(
                    PokerContestant(1L, 100.0, pairTwos),
                    PokerContestant(2L, 100.0, pairTwosAlt),
                    PokerContestant(3L, 100.0, null),
                )
            )
            assertEquals(150.0, result.payouts[1L])
            assertEquals(150.0, result.payouts[2L])
            assertEquals(0.0, result.payouts[3L])
        }

        @Test
        fun `odd-chip remainder goes to first winner`() {
            // pot = 101 (odd cents would distribute unevenly)
            val result = PokerSidePotDistribution.distribute(
                listOf(
                    PokerContestant(1L, 50.5, pairTwos),
                    PokerContestant(2L, 50.5, pairTwosAlt),
                )
            )
            val sum = (result.payouts[1L] ?: 0.0) + (result.payouts[2L] ?: 0.0)
            assertEquals(101.0, sum, 0.0001)
        }
    }

    @Nested
    inner class SidePots {

        @Test
        fun `single all-in below main creates one side pot`() {
            // P1 all-in for 50, P2 and P3 each put in 200.
            // Main pot (eligible: P1, P2, P3) = 50*3 = 150
            // Side pot 1 (eligible: P2, P3) = 150*2 = 300
            // P1 best hand → wins main 150
            // Between P2/P3, P2 better → wins side 300
            val result = PokerSidePotDistribution.distribute(
                listOf(
                    PokerContestant(1L, 50.0, royalFlush),
                    PokerContestant(2L, 200.0, fourKings),
                    PokerContestant(3L, 200.0, fullHouseThrees),
                )
            )
            assertEquals(150.0, result.payouts[1L])
            assertEquals(300.0, result.payouts[2L])
            assertEquals(0.0, result.payouts[3L])
            assertEquals(2, result.pots.size)
            assertEquals(150.0, result.pots[0].amount)
            assertEquals(300.0, result.pots[1].amount)
            assertEquals(listOf(1L), result.pots[0].winnerIds)
            assertEquals(listOf(2L), result.pots[1].winnerIds)
        }

        @Test
        fun `two all-in levels create main + two side pots`() {
            // P1 all-in 30, P2 all-in 80, P3 calls 150
            // Layer 1: 30 × 3 = 90 (eligible: P1, P2, P3)
            // Layer 2: 50 × 2 = 100 (eligible: P2, P3)
            // Layer 3: 70 × 1 = 70 (eligible: P3)
            // Best hands: P1 royal, P2 fourKings, P3 fullHouseThrees
            // P1 wins layer 1 = 90
            // P2 wins layer 2 = 100
            // P3 wins layer 3 alone = 70 (uncontested)
            val result = PokerSidePotDistribution.distribute(
                listOf(
                    PokerContestant(1L, 30.0, royalFlush),
                    PokerContestant(2L, 80.0, fourKings),
                    PokerContestant(3L, 150.0, fullHouseThrees),
                )
            )
            assertEquals(90.0, result.payouts[1L])
            assertEquals(100.0, result.payouts[2L])
            assertEquals(70.0, result.payouts[3L])
            assertEquals(3, result.pots.size)
        }

        @Test
        fun `all-in player loses both pots when behind`() {
            // P1 all-in 50 with weak hand, P2 calls 150, P3 calls 150
            // Main: 50*3=150 (all eligible) → P2 wins (best of all three)
            // Side: 100*2=200 (P2, P3) → P2 wins
            val result = PokerSidePotDistribution.distribute(
                listOf(
                    PokerContestant(1L, 50.0, pairTwos),
                    PokerContestant(2L, 150.0, fourKings),
                    PokerContestant(3L, 150.0, fullHouseThrees),
                )
            )
            assertEquals(0.0, result.payouts[1L])
            assertEquals(350.0, result.payouts[2L])
            assertEquals(0.0, result.payouts[3L])
        }

        @Test
        fun `side pot tie splits between eligible players`() {
            // P1 all-in 40 with weak high-card
            // P2 contributes 100 with pairTwos
            // P3 contributes 100 with pairTwosAlt (same value as pairTwos)
            // Main: 40*3=120 — best is P2/P3 tie → split 60/60
            // Side: 60*2=120 — P2/P3 tie → split 60/60
            // Total: P1 = 0, P2 = 120, P3 = 120
            val result = PokerSidePotDistribution.distribute(
                listOf(
                    PokerContestant(1L, 40.0, PokerHand.fromString("4H 5S 7D 9C JH")),
                    PokerContestant(2L, 100.0, pairTwos),
                    PokerContestant(3L, 100.0, pairTwosAlt),
                )
            )
            assertEquals(0.0, result.payouts[1L])
            assertEquals(120.0, result.payouts[2L])
            assertEquals(120.0, result.payouts[3L])
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `empty contestants returns empty distribution`() {
            val result = PokerSidePotDistribution.distribute(emptyList())
            assertTrue(result.payouts.isEmpty())
            assertTrue(result.pots.isEmpty())
        }

        @Test
        fun `single player with whole contribution wins their stake`() {
            val result = PokerSidePotDistribution.distribute(
                listOf(PokerContestant(1L, 50.0, fourKings))
            )
            assertEquals(50.0, result.payouts[1L])
        }

        @Test
        fun `all folded — pot is unclaimed in layer breakdown`() {
            val result = PokerSidePotDistribution.distribute(
                listOf(
                    PokerContestant(1L, 50.0, null),
                    PokerContestant(2L, 50.0, null),
                )
            )
            assertEquals(0.0, result.payouts[1L])
            assertEquals(0.0, result.payouts[2L])
            assertEquals(1, result.pots.size)
            assertTrue(result.pots[0].winnerIds.isEmpty())
        }
    }
}
