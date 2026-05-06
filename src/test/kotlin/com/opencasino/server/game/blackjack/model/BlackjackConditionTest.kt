package com.opencasino.server.game.blackjack.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BlackjackConditionTest {

    @Test
    fun `five conditions exist`() {
        assertEquals(5, BlackjackCondition.entries.size)
    }

    @Test
    fun `all conditions are present`() {
        val expected = setOf(
            BlackjackCondition.PlayerWin,
            BlackjackCondition.PlayerWinBlackjack,
            BlackjackCondition.DealerBlackjack,
            BlackjackCondition.DealerWin,
            BlackjackCondition.Draw,
            BlackjackCondition.None
        )
        assertEquals(expected, BlackjackCondition.entries.toSet())
    }

    @Test
    fun `valueOf works for all entries`() {
        for (condition in BlackjackCondition.entries) {
            assertEquals(condition, BlackjackCondition.valueOf(condition.name))
        }
    }

    @Test
    fun `valueOf throws for invalid name`() {
        assertThrows(IllegalArgumentException::class.java) {
            BlackjackCondition.valueOf("Push")
        }
    }

    @Test
    fun `toString returns name`() {
        assertEquals("PlayerWin", BlackjackCondition.PlayerWin.toString())
        assertEquals("DealerWin", BlackjackCondition.DealerWin.toString())
        assertEquals("Draw", BlackjackCondition.Draw.toString())
        assertEquals("DealerBlackjack", BlackjackCondition.DealerBlackjack.toString())
        assertEquals("PlayerWinBlackjack", BlackjackCondition.PlayerWinBlackjack.toString())
        assertEquals("None", BlackjackCondition.None.toString())
    }
}