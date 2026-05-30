package com.opencasino.server.rng

import java.math.BigInteger

/**
 * seed → crashPoint. Inverse-CDF в целочисленной арифметике:
 * `P(crash ≥ m) = (1 − houseEdge) / m`, поэтому RTP = `1 − houseEdge` для любой
 * точки cash-out. Целочисленный `floor` вместо `double` — чтобы клиент пересчитал
 * crashPoint из раскрытого seed бит-в-бит (FP-деление разошлось бы между языками).
 *
 * `h` — старшие 52 бита HMAC (как у bustabit: первые 13 hex). При малом `h`
 * (`crashH < 100`) — instant-bust 1.00, его доля ≈ houseEdge. maxPayout — капа на
 * crashPoint (обвал не позже неё), детерминированная и одинаковая для всех.
 */
class CrashOutcomeProvider(
    private val profile: RngProfile,
) : OutcomeProvider<Double> {
    override val houseEdge: Double = profile.houseEdge

    override fun fromHmac(mac: ByteArray): Double {
        val h = topFiftyTwoBits(mac)
        val edge = BigInteger.valueOf(Math.round(profile.houseEdge * E_DOUBLE))
        val numerator = (E - edge) * HUNDRED
        val crashHundredths = numerator / (E - h) // floor: оба операнда > 0
        if (crashHundredths < HUNDRED) return INSTANT_BUST
        val capHundredths = BigInteger.valueOf(Math.round(profile.maxPayout * 100.0))
        return crashHundredths.min(capHundredths).toLong() / 100.0
    }

    private fun topFiftyTwoBits(mac: ByteArray): BigInteger {
        require(mac.size >= 7) { "mac too short for 52-bit extraction" }
        return BigInteger(1, mac.copyOfRange(0, 7)).shiftRight(4)
    }

    companion object {
        private val E: BigInteger = BigInteger.TWO.pow(52)
        private const val E_DOUBLE = 4_503_599_627_370_496.0 // 2^52
        private val HUNDRED: BigInteger = BigInteger.valueOf(100)
        private const val INSTANT_BUST = 1.00
    }
}
