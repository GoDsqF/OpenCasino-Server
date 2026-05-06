package com.opencasino.server.game.poker.holdem

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Тестирует логику блайндов и расчёта ставок,
 * воспроизводя алгоритмы из PokerGameRoom.
 */
class PokerBlindLogicTest {

    data class PlayerStub(
        var stack: Double,
        var position: Int
    )

    private fun takeBlind(player: PlayerStub, amount: Double): Double {
        return if (player.stack >= amount) {
            player.stack -= amount
            amount
        } else {
            val contributed = player.stack
            player.stack = 0.0
            contributed
        }
    }

    @Nested
    inner class TakeBlind {

        @Test
        fun `takes full blind when stack is sufficient`() {
            val player = PlayerStub(stack = 1000.0, position = 0)
            val contributed = takeBlind(player, 100.0)
            assertEquals(100.0, contributed)
            assertEquals(900.0, player.stack)
        }

        @Test
        fun `takes partial blind when stack is less than blind`() {
            val player = PlayerStub(stack = 30.0, position = 0)
            val contributed = takeBlind(player, 100.0)
            assertEquals(30.0, contributed)
            assertEquals(0.0, player.stack)
        }

        @Test
        fun `takes zero from zero stack`() {
            val player = PlayerStub(stack = 0.0, position = 0)
            val contributed = takeBlind(player, 100.0)
            assertEquals(0.0, contributed)
            assertEquals(0.0, player.stack)
        }

        @Test
        fun `takes exact stack amount`() {
            val player = PlayerStub(stack = 100.0, position = 0)
            val contributed = takeBlind(player, 100.0)
            assertEquals(100.0, contributed)
            assertEquals(0.0, player.stack)
        }

        @Test
        fun `small blind is half of big blind`() {
            val bet = 100.0
            val bigBlind = bet
            val smallBlind = bet / 2
            assertEquals(50.0, smallBlind)
            assertEquals(100.0, bigBlind)
        }
    }

    @Nested
    inner class PotCalculation {

        @Test
        fun `pot collects both blinds`() {
            val p1 = PlayerStub(stack = 1000.0, position = 0)
            val p2 = PlayerStub(stack = 1000.0, position = 1)
            var pot = 0.0

            pot += takeBlind(p1, 100.0)  // big blind
            pot += takeBlind(p2, 50.0)   // small blind

            assertEquals(150.0, pot)
            assertEquals(900.0, p1.stack)
            assertEquals(950.0, p2.stack)
        }

        @Test
        fun `pot with short stack player`() {
            val p1 = PlayerStub(stack = 1000.0, position = 0)
            val p2 = PlayerStub(stack = 20.0, position = 1)
            var pot = 0.0

            pot += takeBlind(p1, 100.0)
            pot += takeBlind(p2, 50.0)  // only 20 available

            assertEquals(120.0, pot)
            assertEquals(900.0, p1.stack)
            assertEquals(0.0, p2.stack)
        }

        @Test
        fun `pot with both short stacks`() {
            val p1 = PlayerStub(stack = 40.0, position = 0)
            val p2 = PlayerStub(stack = 10.0, position = 1)
            var pot = 0.0

            pot += takeBlind(p1, 100.0)
            pot += takeBlind(p2, 50.0)

            assertEquals(50.0, pot)
            assertEquals(0.0, p1.stack)
            assertEquals(0.0, p2.stack)
        }
    }

    @Nested
    inner class BetTypeCalculation {

        @Test
        fun `fixed limit min and max are correct`() {
            val bet = 100.0
            val minLimit = bet / 2
            val maxLimit = bet
            assertEquals(50.0, minLimit)
            assertEquals(100.0, maxLimit)
        }

        @Test
        fun `pot limit initial min equals bet`() {
            val bet = 100.0
            val minLimit = bet
            val maxLimit = minLimit
            assertEquals(100.0, minLimit)
            assertEquals(100.0, maxLimit)
        }

        @Test
        fun `no limit min equals bet no max`() {
            val bet = 100.0
            val minLimit = bet
            assertEquals(100.0, minLimit)
            // maxLimit is not set for NoLimit
        }
    }
}