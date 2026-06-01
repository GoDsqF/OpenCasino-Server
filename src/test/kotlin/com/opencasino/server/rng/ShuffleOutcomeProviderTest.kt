package com.opencasino.server.rng

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ShuffleOutcomeProviderTest {
    private val provider = ShuffleOutcomeProvider(deckSize = 52)

    @Test
    fun `output is a permutation of 0 until deckSize`() {
        val perm = provider.fromHmac(ByteArray(32) { it.toByte() })
        assertEquals((0 until 52).toList(), perm.sorted())
    }

    @Test
    fun `same mac yields same permutation`() {
        val mac = ByteArray(32) { (it * 7).toByte() }
        assertEquals(provider.fromHmac(mac), provider.fromHmac(mac))
    }

    @Test
    fun `different mac yields different permutation`() {
        val a = provider.fromHmac(ByteArray(32) { 0x01 })
        val b = provider.fromHmac(ByteArray(32) { 0x02 })
        assertNotEquals(a, b)
    }

    @Test
    fun `has no house edge`() {
        assertEquals(null, provider.houseEdge)
    }
}
