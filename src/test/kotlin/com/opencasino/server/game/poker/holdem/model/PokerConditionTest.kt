package com.opencasino.server.game.poker.holdem.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PokerConditionTest {

    @Test
    fun `three conditions exist`() {
        assertEquals(3, PokerCondition.entries.size)
    }

    @Test
    fun `Win exists`() {
        assertNotNull(PokerCondition.Win)
    }

    @Test
    fun `Lose exists`() {
        assertNotNull(PokerCondition.Lose)
    }

    @Test
    fun `Tie exists`() {
        assertNotNull(PokerCondition.Tie)
    }

    @Test
    fun `valueOf works correctly`() {
        assertEquals(PokerCondition.Win, PokerCondition.valueOf("Win"))
        assertEquals(PokerCondition.Lose, PokerCondition.valueOf("Lose"))
        assertEquals(PokerCondition.Tie, PokerCondition.valueOf("Tie"))
    }
}