package com.opencasino.server.game.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RankTest {

    @Test
    fun `forCode returns correct rank for valid codes`() {
        assertEquals(Rank.C2, Rank.forCode("2"))
        assertEquals(Rank.C3, Rank.forCode("3"))
        assertEquals(Rank.C10, Rank.forCode("1"))
        assertEquals(Rank.CJ, Rank.forCode("J"))
        assertEquals(Rank.CQ, Rank.forCode("Q"))
        assertEquals(Rank.CK, Rank.forCode("K"))
        assertEquals(Rank.CA, Rank.forCode("A"))
    }

    @Test
    fun `forCode with char returns correct rank`() {
        assertEquals(Rank.CA, Rank.forCode('A'))
        assertEquals(Rank.CK, Rank.forCode('K'))
        assertEquals(Rank.C2, Rank.forCode('2'))
    }

    @Test
    fun `forCode throws for invalid code`() {
        assertThrows<RuntimeException> {
            Rank.forCode("Z")
        }
    }

    @Test
    fun `ranks have correct ordinal order`() {
        assertTrue(Rank.C2.ordinal < Rank.C3.ordinal)
        assertTrue(Rank.C3.ordinal < Rank.C4.ordinal)
        assertTrue(Rank.C9.ordinal < Rank.C10.ordinal)
        assertTrue(Rank.C10.ordinal < Rank.CJ.ordinal)
        assertTrue(Rank.CJ.ordinal < Rank.CQ.ordinal)
        assertTrue(Rank.CQ.ordinal < Rank.CK.ordinal)
        assertTrue(Rank.CK.ordinal < Rank.CA.ordinal)
    }

    @Test
    fun `next returns following rank`() {
        assertEquals(Rank.C3, Rank.C2.next())
        assertEquals(Rank.CJ, Rank.C10.next())
        assertEquals(Rank.CQ, Rank.CJ.next())
        assertEquals(Rank.CK, Rank.CQ.next())
        assertEquals(Rank.CA, Rank.CK.next())
    }

    @Test
    fun `CA has no next`() {
        assertNull(Rank.CA.next())
    }

    @Test
    fun `hasNext is true for all except CA`() {
        for (rank in Rank.entries) {
            if (rank == Rank.CA) {
                assertFalse(rank.hasNext())
            } else {
                assertTrue(rank.hasNext(), "$rank should have next")
            }
        }
    }

    @Test
    fun `all 13 ranks exist`() {
        assertEquals(13, Rank.entries.size)
    }
}