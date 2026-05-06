package com.opencasino.server.game.poker.holdem

import com.opencasino.server.service.shared.PokerDecision
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Тестирует валидацию ставок, воспроизводя isValidBet из PokerPlayer.
 */
class PokerBetValidationTest {

    private fun isValidBet(
        amount: Double?,
        lastBet: Double?,
        lastMaxBet: Double,
        bigBlind: Double,
        betType: PokerDecision
    ): Boolean = when (betType) {
        PokerDecision.CALL -> {
            (amount != null) && (amount > 0) && (lastBet!! + amount == lastMaxBet)
        }
        PokerDecision.RAISE -> {
            (amount != null) && (amount > 0) && (lastBet!! + amount + bigBlind >= lastMaxBet)
        }
        else -> false
    }

    @Nested
    inner class CallValidation {

        @Test
        fun `valid call matches lastMaxBet`() {
            // lastBet=0, amount=100, lastMaxBet=100: 0+100==100 ✓
            assertTrue(isValidBet(100.0, 0.0, 100.0, 100.0, PokerDecision.CALL))
        }

        @Test
        fun `valid call with partial previous bet`() {
            // lastBet=50, amount=50, lastMaxBet=100: 50+50==100 ✓
            assertTrue(isValidBet(50.0, 50.0, 100.0, 100.0, PokerDecision.CALL))
        }

        @Test
        fun `call with wrong amount fails`() {
            // lastBet=0, amount=50, lastMaxBet=100: 0+50!=100
            assertFalse(isValidBet(50.0, 0.0, 100.0, 100.0, PokerDecision.CALL))
        }

        @Test
        fun `call with overpayment fails`() {
            // lastBet=0, amount=150, lastMaxBet=100: 0+150!=100
            assertFalse(isValidBet(150.0, 0.0, 100.0, 100.0, PokerDecision.CALL))
        }

        @Test
        fun `call with null amount fails`() {
            assertFalse(isValidBet(null, 0.0, 100.0, 100.0, PokerDecision.CALL))
        }

        @Test
        fun `call with zero amount fails`() {
            assertFalse(isValidBet(0.0, 0.0, 100.0, 100.0, PokerDecision.CALL))
        }

        @Test
        fun `call with negative amount fails`() {
            assertFalse(isValidBet(-50.0, 0.0, 100.0, 100.0, PokerDecision.CALL))
        }
    }

    @Nested
    inner class RaiseValidation {

        @Test
        fun `valid raise meets minimum`() {
            // lastBet=0, amount=200, bigBlind=100, lastMaxBet=100
            // 0+200+100=300 >= 100 ✓
            assertTrue(isValidBet(200.0, 0.0, 100.0, 100.0, PokerDecision.RAISE))
        }

        @Test
        fun `minimum valid raise`() {
            // lastBet=0, amount=1, bigBlind=100, lastMaxBet=100
            // 0+1+100=101 >= 100 ✓
            assertTrue(isValidBet(1.0, 0.0, 100.0, 100.0, PokerDecision.RAISE))
        }

        @Test
        fun `raise with existing bet`() {
            // lastBet=50, amount=100, bigBlind=100, lastMaxBet=200
            // 50+100+100=250 >= 200 ✓
            assertTrue(isValidBet(100.0, 50.0, 200.0, 100.0, PokerDecision.RAISE))
        }

        @Test
        fun `raise with null amount fails`() {
            assertFalse(isValidBet(null, 0.0, 100.0, 100.0, PokerDecision.RAISE))
        }

        @Test
        fun `raise with zero amount fails`() {
            assertFalse(isValidBet(0.0, 0.0, 100.0, 100.0, PokerDecision.RAISE))
        }

        @Test
        fun `raise with negative amount fails`() {
            assertFalse(isValidBet(-50.0, 0.0, 100.0, 100.0, PokerDecision.RAISE))
        }
    }

    @Nested
    inner class OtherDecisions {

        @Test
        fun `CHECK is not valid for bet validation`() {
            assertFalse(isValidBet(100.0, 0.0, 100.0, 100.0, PokerDecision.CHECK))
        }

        @Test
        fun `FOLD is not valid for bet validation`() {
            assertFalse(isValidBet(100.0, 0.0, 100.0, 100.0, PokerDecision.FOLD))
        }

        @Test
        fun `ALL_IN is not valid for bet validation`() {
            assertFalse(isValidBet(100.0, 0.0, 100.0, 100.0, PokerDecision.ALL_IN))
        }

        @Test
        fun `NONE is not valid for bet validation`() {
            assertFalse(isValidBet(100.0, 0.0, 100.0, 100.0, PokerDecision.NONE))
        }
    }
}