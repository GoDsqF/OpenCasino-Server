package com.opencasino.server.rng

import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Фасад provably-fair RNG: commit (до ставок) → derive исход → reveal (после).
 * Сервер — единственный источник истины: исход полностью определён в `commit` из
 * зафиксированного seed, но `serverSeed` держится в памяти и раскрывается лишь в
 * `reveal`; наружу до этого уходит только `serverSeedHash` (commit).
 *
 * Сообщение HMAC — `"$clientSeed:$roundId"` (UTF-8). Клиент повторяет его при
 * верификации (см. [verify]).
 *
 * NB (R1): курсор цепочки и карта неоткрытых seed'ов — in-memory. Continuity между
 * рестартами и сорсинг `clientSeed` в multi — отдельный шаг (R4).
 */
@Service
class RandomnessService(
    private val repository: ProvablyFairRoundRepository,
    private val chain: ProvablyFairChain = ProvablyFairChain.random(DEFAULT_CHAIN_LENGTH),
) {
    private val cursor = AtomicInteger(chain.length - 1)
    private val unrevealed = ConcurrentHashMap<UUID, ByteArray>()
    private val reserveLock = Any()

    data class RoundCommit<out T>(
        val roundId: UUID,
        val serverSeedHash: String,
        val clientSeed: String,
        val outcome: T,
    )

    fun <T> commit(
        gameType: String,
        clientSeed: String,
        provider: OutcomeProvider<T>,
    ): Mono<RoundCommit<T>> {
        val roundId = UUID.randomUUID()
        return Mono
            .fromCallable { reserveAndDerive(roundId, gameType, clientSeed, provider) }
            .flatMap { (entity, commit) -> repository.insert(entity).thenReturn(commit) }
    }

    fun reveal(roundId: UUID): Mono<String> {
        val serverSeed = unrevealed.remove(roundId) ?: return Mono.empty()
        val revealedHex = ProvablyFairChain.toHex(serverSeed)
        return repository.markRevealed(roundId, revealedHex).thenReturn(revealedHex)
    }

    fun <T> verify(
        serverSeedHex: String,
        serverSeedHashHex: String,
        clientSeed: String,
        roundId: UUID,
        expectedOutcome: T,
        provider: OutcomeProvider<T>,
    ): Boolean {
        val serverSeed = ProvablyFairChain.fromHex(serverSeedHex)
        if (ProvablyFairChain.toHex(ProvablyFairChain.commitHash(serverSeed)) != serverSeedHashHex) return false
        val mac = hmac(serverSeed, message(clientSeed, roundId))
        return provider.fromHmac(mac) == expectedOutcome
    }

    private fun <T> reserveAndDerive(
        roundId: UUID,
        gameType: String,
        clientSeed: String,
        provider: OutcomeProvider<T>,
    ): Pair<ProvablyFairRound, RoundCommit<T>> {
        val serverSeed =
            synchronized(reserveLock) {
                val index = cursor.getAndDecrement()
                check(index >= 0) { "provably-fair chain exhausted" }
                chain.seedAt(index)
            }
        val serverSeedHash = ProvablyFairChain.toHex(ProvablyFairChain.commitHash(serverSeed))
        val mac = hmac(serverSeed, message(clientSeed, roundId))
        val outcome = provider.fromHmac(mac)
        unrevealed[roundId] = serverSeed
        val entity =
            ProvablyFairRound(
                roundId = roundId,
                gameType = gameType,
                serverSeedHash = serverSeedHash,
                clientSeed = clientSeed,
                outcome = outcome.toString(),
                houseEdge = provider.houseEdge,
            )
        return entity to RoundCommit(roundId, serverSeedHash, clientSeed, outcome)
    }

    private fun message(
        clientSeed: String,
        roundId: UUID,
    ): ByteArray = "$clientSeed:$roundId".toByteArray(Charsets.UTF_8)

    private fun hmac(
        key: ByteArray,
        message: ByteArray,
    ): ByteArray =
        Mac.getInstance(HMAC_ALGORITHM).run {
            init(SecretKeySpec(key, HMAC_ALGORITHM))
            doFinal(message)
        }

    companion object {
        const val DEFAULT_CHAIN_LENGTH = 1_000_000
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
