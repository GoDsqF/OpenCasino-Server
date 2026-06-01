package com.opencasino.server.rng

import java.security.MessageDigest

/**
 * seed → перестановка `[0, deckSize)` (Fisher-Yates). Детерминированно из `mac`:
 * та же пара (serverSeed, clientSeed) даёт ту же раздачу → проверяемо клиентом.
 * Честность «по построению» (равномерная перестановка), поэтому `houseEdge = null`.
 *
 * NB (R1): класс реализован, но НЕ подключён к `CardDeck`. Вайринг shuffle и reveal
 * для Blackjack/Poker — отдельный шаг R5 (см. CRASH.md §10, CLAUDE.md).
 *
 * 32-байт `mac` мало для большой колоды, поэтому энтропия растягивается
 * counter-DRBG'ом `SHA256(mac ‖ counterBE)`; индекс в `[0, bound)` берётся
 * rejection sampling'ом (без modulo-bias).
 */
class ShuffleOutcomeProvider(
    private val deckSize: Int,
) : OutcomeProvider<List<Int>> {
    override val houseEdge: Double? = null

    override fun fromHmac(mac: ByteArray): List<Int> {
        val stream = CounterStream(mac)
        val result = (0 until deckSize).toMutableList()
        for (i in deckSize - 1 downTo 1) {
            val j = stream.nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result
    }

    private class CounterStream(
        private val seed: ByteArray,
    ) {
        private val digest = MessageDigest.getInstance("SHA-256")
        private var counter = 0L
        private var block = ByteArray(0)
        private var offset = 0

        fun nextInt(bound: Int): Int {
            require(bound > 0)
            val limit = MAX_UINT32 - (MAX_UINT32 % bound) // отсекаем хвост → нет modulo-bias
            while (true) {
                val candidate = nextUInt32()
                if (candidate < limit) return (candidate % bound).toInt()
            }
        }

        private fun nextUInt32(): Long {
            var value = 0L
            repeat(4) {
                value = (value shl 8) or (nextByte().toLong() and 0xFF)
            }
            return value
        }

        private fun nextByte(): Byte {
            if (offset >= block.size) {
                digest.reset()
                digest.update(seed)
                digest.update(counterToBytes(counter++))
                block = digest.digest()
                offset = 0
            }
            return block[offset++]
        }

        private fun counterToBytes(value: Long): ByteArray =
            ByteArray(8) { i ->
                (value ushr (8 * (7 - i)) and 0xFF).toByte()
            }

        companion object {
            private const val MAX_UINT32 = 1L shl 32
        }
    }
}
