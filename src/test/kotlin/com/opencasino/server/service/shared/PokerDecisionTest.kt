package com.opencasino.server.service.shared

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PokerDecisionTest {

    @Test
    fun `valueOf works for all entries`() {
        for (decision in PokerDecision.entries) {
            assertEquals(decision, PokerDecision.valueOf(decision.name))
        }
    }

    @Test
    fun `valueOf throws for invalid name`() {
        assertThrows(IllegalArgumentException::class.java) {
            PokerDecision.valueOf("BET")
        }
    }

    @Test
    fun `six decisions exist`() {
        assertEquals(6, PokerDecision.entries.size)
    }
}