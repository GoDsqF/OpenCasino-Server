package com.opencasino.server.service.shared

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BlackjackDecisionTest {

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