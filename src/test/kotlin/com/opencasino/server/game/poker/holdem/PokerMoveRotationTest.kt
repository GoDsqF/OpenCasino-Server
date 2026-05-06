package com.opencasino.server.game.poker.holdem

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Тестирует логику ротации ходов между игроками,
 * воспроизводя nextMove из PokerGameRoom.
 */
class PokerMoveRotationTest {

    data class PlayerStub(
        val position: Int,
        var folded: Boolean = false,
        var allin: Boolean = false,
        var currentBet: Double = 0.0
    )

    /**
     * Воспроизводит allBetsValid из PokerGameRoom
     */
    private fun allBetsValid(players: List<PlayerStub>, lastMaxBet: Double): Boolean {
        for (player in players) {
            if (player.currentBet != lastMaxBet) {
                if (!player.folded && !player.allin) return false
            }
        }
        return true
    }

    /**
     * Воспроизводит вычисление следующей позиции из nextMove
     */
    private fun nextPosition(currentPosition: Int, playersCount: Int): Int {
        return if (currentPosition == playersCount - 1) 0
        else currentPosition + 1
    }

    /**
     * Воспроизводит вычисление lastPlayer из nextMove
     */
    private fun lastPlayer(currentStartPlayer: Int, playersCount: Int): Int {
        return if (currentStartPlayer != 0) currentStartPlayer - 1
        else playersCount - 1
    }

    // =========================================================================
    // Ротация позиций
    // =========================================================================

    @Nested
    inner class PositionRotation {

        @Test
        fun `next position wraps around`() {
            assertEquals(0, nextPosition(5, 6))   // last -> first
        }

        @Test
        fun `next position increments normally`() {
            assertEquals(1, nextPosition(0, 6))
            assertEquals(2, nextPosition(1, 6))
            assertEquals(3, nextPosition(2, 6))
        }

        @Test
        fun `next position with 2 players`() {
            assertEquals(1, nextPosition(0, 2))
            assertEquals(0, nextPosition(1, 2))
        }

        @Test
        fun `next position with 3 players cycles correctly`() {
            var pos = 0
            val visited = mutableListOf<Int>()
            repeat(6) {
                visited.add(pos)
                pos = nextPosition(pos, 3)
            }
            assertEquals(listOf(0, 1, 2, 0, 1, 2), visited)
        }

        @Test
        fun `last player when startPlayer is 0`() {
            assertEquals(5, lastPlayer(0, 6))
        }

        @Test
        fun `last player when startPlayer is not 0`() {
            assertEquals(0, lastPlayer(1, 6))
            assertEquals(2, lastPlayer(3, 6))
            assertEquals(4, lastPlayer(5, 6))
        }

        @Test
        fun `last player with 2 players`() {
            assertEquals(1, lastPlayer(0, 2))
            assertEquals(0, lastPlayer(1, 2))
        }
    }

    // =========================================================================
    // Валидация ставок всех игроков
    // =========================================================================

    @Nested
    inner class AllBetsValidTests {

        @Test
        fun `all players matched bet - valid`() {
            val players = listOf(
                PlayerStub(0, currentBet = 100.0),
                PlayerStub(1, currentBet = 100.0),
                PlayerStub(2, currentBet = 100.0)
            )
            assertTrue(allBetsValid(players, 100.0))
        }

        @Test
        fun `one player has not matched bet - invalid`() {
            val players = listOf(
                PlayerStub(0, currentBet = 100.0),
                PlayerStub(1, currentBet = 50.0),
                PlayerStub(2, currentBet = 100.0)
            )
            assertFalse(allBetsValid(players, 100.0))
        }

        @Test
        fun `folded player with lower bet is valid`() {
            val players = listOf(
                PlayerStub(0, currentBet = 100.0),
                PlayerStub(1, currentBet = 0.0, folded = true),
                PlayerStub(2, currentBet = 100.0)
            )
            assertTrue(allBetsValid(players, 100.0))
        }

        @Test
        fun `allin player with lower bet is valid`() {
            val players = listOf(
                PlayerStub(0, currentBet = 100.0),
                PlayerStub(1, currentBet = 30.0, allin = true),
                PlayerStub(2, currentBet = 100.0)
            )
            assertTrue(allBetsValid(players, 100.0))
        }

        @Test
        fun `all folded except one is valid`() {
            val players = listOf(
                PlayerStub(0, currentBet = 100.0),
                PlayerStub(1, currentBet = 0.0, folded = true),
                PlayerStub(2, currentBet = 0.0, folded = true)
            )
            assertTrue(allBetsValid(players, 100.0))
        }

        @Test
        fun `all allin is valid`() {
            val players = listOf(
                PlayerStub(0, currentBet = 50.0, allin = true),
                PlayerStub(1, currentBet = 30.0, allin = true),
                PlayerStub(2, currentBet = 100.0)
            )
            assertTrue(allBetsValid(players, 100.0))
        }

        @Test
        fun `mix of folded and allin with unmatched active - invalid`() {
            val players = listOf(
                PlayerStub(0, currentBet = 100.0),
                PlayerStub(1, currentBet = 0.0, folded = true),
                PlayerStub(2, currentBet = 50.0, allin = true),
                PlayerStub(3, currentBet = 75.0) // active but not matched
            )
            assertFalse(allBetsValid(players, 100.0))
        }

        @Test
        fun `empty players list is valid`() {
            assertTrue(allBetsValid(emptyList(), 100.0))
        }

        @Test
        fun `single player matched is valid`() {
            val players = listOf(PlayerStub(0, currentBet = 100.0))
            assertTrue(allBetsValid(players, 100.0))
        }

        @Test
        fun `single player not matched and active is invalid`() {
            val players = listOf(PlayerStub(0, currentBet = 50.0))
            assertFalse(allBetsValid(players, 100.0))
        }

        @Test
        fun `zero lastMaxBet with zero bets is valid`() {
            val players = listOf(
                PlayerStub(0, currentBet = 0.0),
                PlayerStub(1, currentBet = 0.0)
            )
            assertTrue(allBetsValid(players, 0.0))
        }
    }

    // =========================================================================
    // Полные сценарии ротации
    // =========================================================================

    @Nested
    inner class FullRotationScenarios {

        @Test
        fun `3 players full round - everyone calls`() {
            val players = listOf(
                PlayerStub(0, currentBet = 0.0),
                PlayerStub(1, currentBet = 0.0),
                PlayerStub(2, currentBet = 0.0)
            )
            val lastMaxBet = 100.0
            var currentPos = 0
            val startPlayer = 0

            // Симулируем: каждый делает call
            for (i in players.indices) {
                players[currentPos].currentBet = lastMaxBet
                currentPos = nextPosition(currentPos, players.size)
            }

            // После полного круга все ставки валидны
            assertTrue(allBetsValid(players, lastMaxBet))
        }

        @Test
        fun `4 players - one folds, rest call`() {
            val players = listOf(
                PlayerStub(0, currentBet = 0.0),
                PlayerStub(1, currentBet = 0.0),
                PlayerStub(2, currentBet = 0.0),
                PlayerStub(3, currentBet = 0.0)
            )
            val lastMaxBet = 100.0

            players[0].currentBet = lastMaxBet       // call
            players[1].folded = true                   // fold
            players[2].currentBet = lastMaxBet        // call
            players[3].currentBet = lastMaxBet        // call

            assertTrue(allBetsValid(players, lastMaxBet))
        }

        @Test
        fun `raise forces new round of betting`() {
            val players = mutableListOf(
                PlayerStub(0, currentBet = 0.0),
                PlayerStub(1, currentBet = 0.0),
                PlayerStub(2, currentBet = 0.0)
            )

            // Round 1: P0 calls 100, P1 raises to 200, P2 needs to respond
            players[0].currentBet = 100.0
            players[1].currentBet = 200.0
            players[2].currentBet = 100.0

            val newMaxBet = 200.0
            // Not valid yet — P0 and P2 haven't matched 200
            assertFalse(allBetsValid(players, newMaxBet))

            // Round 2: P0 calls 200, P2 calls 200
            players[0].currentBet = 200.0
            players[2].currentBet = 200.0

            assertTrue(allBetsValid(players, newMaxBet))
        }

        @Test
        fun `allin player does not block round completion`() {
            val players = listOf(
                PlayerStub(0, currentBet = 100.0),
                PlayerStub(1, currentBet = 40.0, allin = true),  // short stack
                PlayerStub(2, currentBet = 100.0)
            )
            assertTrue(allBetsValid(players, 100.0))
        }

        @Test
        fun `position cycles through full table twice`() {
            val playersCount = 4
            var pos = 0
            val fullCycle = mutableListOf<Int>()

            repeat(playersCount * 2) {
                fullCycle.add(pos)
                pos = nextPosition(pos, playersCount)
            }

            assertEquals(
                listOf(0, 1, 2, 3, 0, 1, 2, 3),
                fullCycle
            )
        }

        @Test
        fun `start player rotation across rounds`() {
            val playersCount = 4
            var startPlayer = 0

            val starts = mutableListOf<Int>()
            repeat(playersCount) {
                starts.add(startPlayer)
                startPlayer = nextPosition(startPlayer, playersCount)
            }

            assertEquals(listOf(0, 1, 2, 3), starts)
        }

        @Test
        fun `heads up rotation is correct`() {
            // 2 players — каждый ход меняется позиция
            val playersCount = 2
            var pos = 0

            assertEquals(0, pos)
            pos = nextPosition(pos, playersCount)
            assertEquals(1, pos)
            pos = nextPosition(pos, playersCount)
            assertEquals(0, pos)
        }
    }
}