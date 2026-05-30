package com.opencasino.server.rng

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ProvablyFairChainTest {
    private val terminal = ByteArray(32) { 0x5A }
    private val chain = ProvablyFairChain(terminal, length = 100)

    @Test
    fun `terminal seed sits at the last index`() {
        assertArrayEquals(terminal, chain.seedAt(99))
    }

    @Test
    fun `commit hash of seed i equals seed i-1 (chain linkage)`() {
        for (i in 99 downTo 1) {
            assertArrayEquals(ProvablyFairChain.commitHash(chain.seedAt(i)), chain.seedAt(i - 1))
        }
    }

    @Test
    fun `seedAt is stable across calls and indices differ`() {
        assertArrayEquals(chain.seedAt(50), chain.seedAt(50))
        assertFalse(chain.seedAt(50).contentEquals(chain.seedAt(49)))
    }

    @Test
    fun `hex round-trips`() {
        val seed = chain.seedAt(0)
        assertArrayEquals(seed, ProvablyFairChain.fromHex(ProvablyFairChain.toHex(seed)))
        assertEquals(64, ProvablyFairChain.toHex(seed).length)
    }
}
