package com.opencasino.server.service.shared

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PokerDecisionTest {

    @Test
    fun `all decisions have unique type codes`() {
        val types = PokerDecision.entries.map { it.type }
        assertEquals(types.size, types.toSet().size)
    }

    @Test
    fun `CHECK has type 20`() {
        assertEquals(20, PokerDecision.CHECK.type)
    }

    @Test
    fun `CALL has type 21`() {
        assertEquals(21, PokerDecision.CALL.type)
    }

    @Test
    fun `RAISE has type 22`() {
        assertEquals(22, PokerDecision.RAISE.type)
    }

    @Test
    fun `FOLD has type 23`() {
        assertEquals(23, PokerDecision.FOLD.type)
    }

    @Test
    fun `ALL_IN has type 24`() {
        assertEquals(24, PokerDecision.ALL_IN.type)
    }

    @Test
    fun `NONE has type 44`() {
        assertEquals(44, PokerDecision.NONE.type)
    }

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