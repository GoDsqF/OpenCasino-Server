package com.opencasino.server.network.pack.shared

/**
 * Reveal перетасовки колоды (provably-fair, R5). После раздачи (Poker — на showdown;
 * Blackjack — при выходе шуза из игры) сервер раскрывает `revealedServerSeed`. Любой
 * клиент проверяет `SHA256(revealedServerSeed) == serverSeedHash` и что перестановка
 * выводится из `HMAC(revealedServerSeed, "$clientSeed:$roundId")` через
 * `ShuffleOutcomeProvider` (см. RandomnessService.verify) — сверяет с наблюдаемым
 * порядком сданных карт.
 */
data class ShuffleRevealPack(
    val roundId: String,
    val gameType: String,
    val serverSeedHash: String,
    val revealedServerSeed: String,
    val clientSeed: String,
)
