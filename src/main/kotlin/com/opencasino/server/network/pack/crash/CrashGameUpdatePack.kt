package com.opencasino.server.network.pack.crash

import com.opencasino.server.network.pack.UpdatePack
import com.opencasino.server.service.shared.CrashPhase

/**
 * Per-recipient снимок раунда Crash (CRASH.md §4.2). Сервер — единственный
 * источник истины о множителе: шлёт `roundStartEpochMs` + `growthRate` (из settings),
 * клиент интерполирует кривую локально. `serverMultiplier` — серверное значение на
 * момент тика, для resync и детекта обвала, НЕ «истина для анимации».
 *
 * `tickSeq` + `tickSentEpochMs` — якорь honest cash-out при лаге (CRASH.md §5.3):
 * клиент в CRASH_CASHOUT называет увиденный `lastTickSeq`, сервер засчитывает
 * множитель кадра, который сам же выпустил.
 */
data class CrashGameUpdatePack(
    val phase: CrashPhase,
    // Эпоха старта кривой (фаза RUNNING); null в остальных фазах.
    val roundStartEpochMs: Long?,
    // Дедлайн окна ставок (фаза BETTING) / конца паузы (фаза COOLDOWN); null иначе.
    val bettingDeadlineEpochMs: Long?,
    val cooldownDeadlineEpochMs: Long?,
    val tickSeq: Long,
    val tickSentEpochMs: Long,
    val serverMultiplier: Double,
    val crashHistory: List<Double>,
    val serverSeedHash: String,
    val players: List<CrashPlayerView>,
) : UpdatePack
