package com.opencasino.server.network.pack.crash

/**
 * Per-player исход раунда. `outcome` считает сервер (FE-driven, CRASH.md §2.6):
 * клиент не комбинирует поля и не сравнивает с порогами. WIN — вывелся до обвала,
 * LOSE — не успел.
 */
data class CrashResultEntry(
    val id: Long,
    val displayName: String,
    val outcome: String,
    val stake: Double,
    val cashedOutAt: Double?,
    val payout: Double,
)
