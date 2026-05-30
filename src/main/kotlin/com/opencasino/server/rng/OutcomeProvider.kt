package com.opencasino.server.rng

/**
 * Маппит сырой HMAC раунда в исход конкретной игры. Чистая детерминированная
 * функция: тот же `mac` всегда даёт тот же исход, что и делает игру проверяемой
 * клиентом (provably-fair). HMAC считает [RandomnessService]; провайдер не знает
 * ни про seed-цепочку, ни про БД.
 *
 * `houseEdge` — для аудита (`provably_fair_round.house_edge`); `null`, если у игры
 * нет RNG-edge (напр. перетасовка колоды — честность «по построению»).
 */
interface OutcomeProvider<out T> {
    val houseEdge: Double?

    fun fromHmac(mac: ByteArray): T
}
