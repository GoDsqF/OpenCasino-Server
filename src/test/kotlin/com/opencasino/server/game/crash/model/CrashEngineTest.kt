package com.opencasino.server.game.crash.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrashEngineTest {
    private val engine = CrashEngine(growthRate = 1.07)

    @Test
    fun `multiplier starts at 1 and is monotonic non-decreasing`() {
        assertEquals(1.0, engine.multiplierAt(0))
        assertEquals(1.0, engine.multiplierAt(-100))
        var previous = engine.multiplierAt(0)
        for (ms in 0..30_000 step 250) {
            val current = engine.multiplierAt(ms.toLong())
            assertTrue(current >= previous, "multiplier decreased at $ms ms: $current < $previous")
            previous = current
        }
    }

    @Test
    fun `multiplier is quantised to hundredths`() {
        for (ms in 0..20_000 step 137) {
            val m = engine.multiplierAt(ms.toLong())
            assertEquals(Math.rint(m * 100) / 100.0, m, 1e-9, "not a hundredth at $ms ms: $m")
        }
    }

    @Test
    fun `crashElapsedMs is the inverse of multiplierAt`() {
        for (cp in listOf(1.01, 1.5, 2.0, 3.33, 10.0, 100.0)) {
            val t = engine.crashElapsedMs(cp)
            assertTrue(engine.multiplierAt(t) >= cp, "m($t) < $cp")
            // Один шаг квантования раньше — кривая ещё не дошла до crashPoint.
            assertTrue(engine.multiplierAt(t - 50) < cp, "m(${t - 50}) >= $cp (crashed too early)")
        }
    }

    @Test
    fun `crashElapsedMs is zero at or below base`() {
        assertEquals(0L, engine.crashElapsedMs(1.0))
        assertEquals(0L, engine.crashElapsedMs(0.5))
    }
}
