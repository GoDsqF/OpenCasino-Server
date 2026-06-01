package com.opencasino.server.network.pack.crash

/**
 * Reveal раунда (CRASH.md §4.2). После обвала сервер раскрывает `revealedServerSeed`;
 * любой клиент проверяет `SHA256(revealedServerSeed) == serverSeedHash` и что
 * `crashPoint` выводится из `HMAC(revealedServerSeed, "$clientSeed:$roundId")`
 * (см. RandomnessService.verify).
 */
data class CrashRoundResultPack(
    val roundId: String,
    val crashPoint: Double,
    val serverSeedHash: String,
    val revealedServerSeed: String,
    val clientSeed: String,
    val players: List<CrashResultEntry>,
)
