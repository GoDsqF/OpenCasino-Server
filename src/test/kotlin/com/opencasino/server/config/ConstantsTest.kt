package com.opencasino.server.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConstantsTest {

    @Test
    fun `loop rate is positive`() {
        assertTrue(DEFAULT_LOOP_RATE > 0)
    }

    @Test
    fun `room start delay is positive`() {
        assertTrue(ROOM_START_DELAY > 0)
    }

    @Test
    fun `room end delay is greater than start delay`() {
        assertTrue(ROOM_END_DELAY > ROOM_START_DELAY)
    }

    @Test
    fun `max blackjack players is at least 1`() {
        assertTrue(MAX_BLACKJACK_PLAYERS >= 1)
    }

    @Test
    fun `max poker players greater than min`() {
        assertTrue(MAX_POKER_PLAYERS > MIN_POKER_PLAYERS)
    }

    @Test
    fun `min poker players at least 2`() {
        assertTrue(MIN_POKER_PLAYERS >= 2)
    }

    @Test
    fun `min blackjack bet is positive`() {
        assertTrue(MIN_BLACKJACK_BET > 0)
    }

    @Test
    fun `min poker bet is positive`() {
        assertTrue(MIN_POKER_BET > 0)
    }

    @Test
    fun `max poker bet greater than or equal to min`() {
        assertTrue(MAX_POKER_BET >= MIN_POKER_BET)
    }

    @Test
    fun `poker buy in is positive`() {
        assertTrue(POKER_BUY_IN > 0)
    }
}