package com.opencasino.server.game.poker.holdem.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PokerBetTypeTest {

    @Test
    fun `three bet types exist`() {
        assertEquals(3, PokerBetType.entries.size)
    }

    @Test
    fun `FixedLimit exists`() {
        assertNotNull(PokerBetType.FixedLimit)
    }

    @Test
    fun `PotLimit exists`() {
        assertNotNull(PokerBetType.PotLimit)
    }

    @Test
    fun `NoLimit exists`() {
        assertNotNull(PokerBetType.NoLimit)
    }

    @Test
    fun `valueOf works for all entries`() {
        for (type in PokerBetType.entries) {
            assertEquals(type, PokerBetType.valueOf(type.name))
        }
    }

    @Test
    fun `valueOf throws for invalid name`() {
        assertThrows(IllegalArgumentException::class.java) {
            PokerBetType.valueOf("SpreadLimit")
        }
    }
}