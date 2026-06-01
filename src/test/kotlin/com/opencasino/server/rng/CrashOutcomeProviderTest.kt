package com.opencasino.server.rng

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class CrashOutcomeProviderTest {
    private val provider = CrashOutcomeProvider(RngProfile(houseEdge = 0.03, maxPayout = 1000.0))

    @Test
    fun `same mac yields same crashPoint`() {
        val mac = ByteArray(32) { it.toByte() }
        assertEquals(provider.fromHmac(mac), provider.fromHmac(mac))
    }

    @Test
    fun `crashPoint is never below 1_00`() {
        val rnd = Random(7)
        val mac = ByteArray(32)
        repeat(10_000) {
            rnd.nextBytes(mac)
            assertTrue(provider.fromHmac(mac) >= 1.0)
        }
    }

    @Test
    fun `smallest h gives instant bust`() {
        // первые 7 байт = 0 → h = 0 → crashH < 100 → instant-bust
        assertEquals(1.00, provider.fromHmac(ByteArray(32)))
    }

    @Test
    fun `largest h is capped at maxPayout`() {
        // первые 7 байт = 0xFF → h = 2^52 - 1 → E - h = 1 → crashH огромен → капа
        val mac = ByteArray(32).also { for (i in 0 until 7) it[i] = 0xFF.toByte() }
        assertEquals(1000.0, provider.fromHmac(mac))
    }

    @Test
    fun `crashPoint equals 1_00 with house-edge mass plus the floor band`() {
        // crashPoint == 1.00 двумя непересекающимися путями: house-edge mass P(h<edge)=houseEdge,
        // плюс нижняя дискретизационная полоса [1.00,1.01) → floor до 1.00 = (1-houseEdge)/101.
        val houseEdge = 0.03
        val expected = houseEdge + (1 - houseEdge) / 101.0
        val rnd = Random(42)
        val mac = ByteArray(32)
        var ones = 0
        repeat(SAMPLES) {
            rnd.nextBytes(mac)
            if (provider.fromHmac(mac) == 1.00) ones++
        }
        assertEquals(expected, ones.toDouble() / SAMPLES, 0.005)
    }

    @Test
    fun `measured RTP converges to 1 minus house edge`() {
        // Игрок ставит 1 и выводит на target t: возврат = t, если crashPoint >= t, иначе 0.
        // Ожидаемый RTP = t * P(crash >= t) = (1 - houseEdge) при любом t.
        assertEquals(0.97, measureRtp(CrashOutcomeProvider(RngProfile(0.03, 1000.0)), target = 2.0), 0.01)
    }

    @Test
    fun `house edge is the knob driving RTP`() {
        assertEquals(0.95, measureRtp(CrashOutcomeProvider(RngProfile(0.05, 1000.0)), target = 2.0), 0.01)
    }

    private fun measureRtp(
        provider: CrashOutcomeProvider,
        target: Double,
    ): Double {
        val rnd = Random(99)
        val mac = ByteArray(32)
        var totalReturn = 0.0
        repeat(SAMPLES) {
            rnd.nextBytes(mac)
            if (provider.fromHmac(mac) >= target) totalReturn += target
        }
        return totalReturn / SAMPLES
    }

    private companion object {
        const val SAMPLES = 500_000
    }
}
