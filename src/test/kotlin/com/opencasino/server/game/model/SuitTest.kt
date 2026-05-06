package com.opencasino.server.game.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SuitTest {

    @Test
    fun `forCode returns correct suit`() {
        assertEquals(Suit.SPADES, Suit.forCode("S"))
        assertEquals(Suit.HEARTS, Suit.forCode("H"))
        assertEquals(Suit.DIAMONDS, Suit.forCode("D"))
        assertEquals(Suit.CLUBS, Suit.forCode("C"))
    }

    @Test
    fun `forCode with char returns correct suit`() {
        assertEquals(Suit.SPADES, Suit.forCode('S'))
        assertEquals(Suit.HEARTS, Suit.forCode('H'))
    }

    @Test
    fun `forCode throws for invalid code`() {
        assertThrows<RuntimeException> {
            Suit.forCode("X")
        }
    }

    @Test
    fun `all 4 suits exist`() {
        assertEquals(4, Suit.entries.size)
    }
}