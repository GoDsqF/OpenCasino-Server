package com.opencasino.server.network.pack.crash

import com.opencasino.server.network.pack.InitPack

/**
 * Настройки стола Crash (зеркало poker GameSettingsPack). `growthRate` нужен клиенту
 * для локальной интерполяции кривой `m(t) = growthRate^(t/1000)` (CRASH.md §5.1).
 * `mode` — SINGLE / MULTI.
 */
data class CrashSettingsPack(
    val roomId: String,
    val mode: String, // ts: CrashMode
    val loopRate: Long,
    val minBet: Double,
    val maxBet: Double,
    val bettingWindowMs: Long,
    val cooldownMs: Long,
    val growthRate: Double,
    val maxPayout: Double,
    val houseEdge: Double, // ts: number
) : InitPack
