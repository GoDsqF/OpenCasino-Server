package com.opencasino.server.rng

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Commit-reveal хэш-цепочка (bustabit-модель). Терминальный `seed[length-1]`
 * рождается из [SecureRandom], дальше `seed[i] = SHA256(seed[i+1])`. Раунды
 * расходуют seed'ы с конца к началу; commit раунда — `SHA256(seed)`, публикуется
 * ДО раунда. После reveal игрок проверяет `SHA256(revealed) == commit` — сервер не
 * мог подменить исход постфактум.
 *
 * Инвариант линковки: `commitHash(seedAt(i)) == seedAt(i - 1)`.
 *
 * Walk мемоизируется вниз от терминального seed, поэтому последовательное
 * потребление с конца — O(1) на раунд.
 *
 * NB (R1): курсор и память живут только в процессе. Персистентность цепочки между
 * рестартами — отдельный шаг (нужен когда комнаты реально крутят раунды, R4).
 */
class ProvablyFairChain(
    terminalSeed: ByteArray,
    val length: Int,
) {
    init {
        require(length > 0) { "chain length must be positive" }
        require(terminalSeed.isNotEmpty()) { "terminal seed must not be empty" }
    }

    private val cache = HashMap<Int, ByteArray>().apply { put(length - 1, terminalSeed) }

    fun seedAt(index: Int): ByteArray {
        require(index in 0 until length) { "index $index out of chain bounds [0, $length)" }
        cache[index]?.let { return it }
        var i = index
        while (cache[i] == null) i++
        while (i > index) {
            val derived = sha256(cache.getValue(i))
            cache[i - 1] = derived
            i--
        }
        return cache.getValue(index)
    }

    companion object {
        fun random(length: Int): ProvablyFairChain = ProvablyFairChain(randomSeed(), length)

        fun commitHash(seed: ByteArray): ByteArray = sha256(seed)

        fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        fun fromHex(hex: String): ByteArray =
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }

        private fun randomSeed(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
