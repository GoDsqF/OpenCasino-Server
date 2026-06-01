package com.opencasino.server.network.pack.crash

/**
 * Публичный вид игрока в раунде Crash. `cashedOutAt` != null — игрок уже вывелся
 * на этом множителе. `autoCashoutTarget` заполняется только для самого получателя
 * (isYou), у остальных null — чужой авто-таргет приватен.
 */
data class CrashPlayerView(
    val id: Long,
    val displayName: String,
    val stake: Double,
    val cashedOutAt: Double?,
    val autoCashoutTarget: Double?,
    val isYou: Boolean,
)
