package com.opencasino.server.game.crash.model

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/**
 * Чистая логика кривой множителя (CRASH.md §0, §3). Множитель — функция только
 * серверного времени с момента старта раунда: `m(t) = growthRate^(t/1000)`.
 * Сервер штампует `roundStartEpochMs` + `growthRate` в UPDATE, клиент рисует ту же
 * кривую локально — число с провода не доверяется.
 *
 * Множитель квантуется по сотым через floor: тот же шаг, что у `crashPoint`
 * ([CrashOutcomeProvider] выдаёт сотые), и floor никогда не переплачивает игроку.
 */
class CrashEngine(
    private val growthRate: Double,
) {
    init {
        require(growthRate > BASE) { "growthRate must be > 1.0, was $growthRate" }
    }

    fun multiplierAt(elapsedMs: Long): Double {
        if (elapsedMs <= 0L) return BASE
        val raw = growthRate.pow(elapsedMs / MS_PER_SECOND)
        return floor(raw * HUNDREDTHS) / HUNDREDTHS
    }

    /**
     * Время от старта раунда, к которому квантованный [multiplierAt] впервые
     * достигает [crashPoint]. `ceil`, чтобы `multiplierAt(crashElapsedMs) >= crashPoint`
     * (обвал не раньше, чем кривая реально дошла до точки).
     */
    fun crashElapsedMs(crashPoint: Double): Long {
        if (crashPoint <= BASE) return 0L
        val seconds = ln(crashPoint) / ln(growthRate)
        return ceil(seconds * MS_PER_SECOND).toLong()
    }

    companion object {
        private const val BASE = 1.0
        private const val MS_PER_SECOND = 1000.0
        private const val HUNDREDTHS = 100.0
    }
}
