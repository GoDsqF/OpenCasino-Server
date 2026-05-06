package com.opencasino.server.service.shared

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BlackjackDecisionTest {

    @Test
    fun `all decisions have unique type codes`() {
        val types = BlackjackDecision.entries.map { it.type }
        Assertions.assertEquals(types.size, types.toSet().size)
    }

    @Test
    fun `HIT has type 20`() {
        Assertions.assertEquals(20, BlackjackDecision.HIT.type)
    }

    @Test
    fun `STAND has type 21`() {
        Assertions.assertEquals(21, BlackjackDecision.STAND.type)
    }

    @Test
    fun `DOUBLE has type 22`() {
        Assertions.assertEquals(22, BlackjackDecision.DOUBLE.type)
    }

    @Test
    fun `SPLIT has type 23`() {
        Assertions.assertEquals(23, BlackjackDecision.SPLIT.type)
    }

    @Test
    fun `NONE has type 44`() {
        Assertions.assertEquals(44, BlackjackDecision.NONE.type)
    }

    @Test
    fun `valueOf works for all entries`() {
        for (decision in BlackjackDecision.entries) {
            Assertions.assertEquals(decision, BlackjackDecision.valueOf(decision.name))
        }
    }

    @Test
    fun `valueOf throws for invalid name`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            BlackjackDecision.valueOf("INVALID")
        }
    }

    @Test
    fun `five decisions exist`() {
        Assertions.assertEquals(5, BlackjackDecision.entries.size)
    }
}