package com.opencasino.server.security

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class RefreshTokenService(
    private val repository: RefreshTokenRepository,
    private val props: JwtProperties,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
    private val auditLogger: SecurityAuditLogger? = null,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun issue(userId: UUID, userAgent: String? = null, ip: String? = null): Mono<IssuedRefresh> {
        val plaintext = generatePlaintext()
        val now = clock.instant()
        val expiresAt = now.plus(props.refreshTtl)
        val token = RefreshToken(
            userId = userId,
            tokenHash = hash(plaintext),
            createdAt = now,
            expiresAt = expiresAt,
            userAgent = userAgent?.take(MAX_USER_AGENT_LENGTH),
            ip = ip?.take(MAX_IP_LENGTH),
        )
        return repository.save(token)
            .map { IssuedRefresh(plaintext = plaintext, expiresAt = expiresAt) }
    }

    fun rotate(plaintext: String, userAgent: String? = null, ip: String? = null): Mono<RotatedRefresh> =
        lookup(plaintext)
            .flatMap { existing ->
                val now = clock.instant()
                when {
                    existing.revokedAt != null -> handleReplay(existing, now)
                    !existing.expiresAt.isAfter(now) ->
                        Mono.error(AuthException(AuthFailureCode.REFRESH_EXPIRED))
                    else ->
                        repository.markRevoked(existing.id, now)
                            .then(issue(existing.userId, userAgent, ip))
                            .map { issued -> RotatedRefresh(userId = existing.userId, refresh = issued) }
                }
            }

    fun revoke(plaintext: String): Mono<Void> =
        lookup(plaintext)
            .flatMap { existing ->
                if (existing.revokedAt != null) Mono.empty()
                else repository.markRevoked(existing.id, clock.instant()).then()
            }
            .then()

    fun revokeAllForUser(plaintext: String): Mono<RevokedAll> =
        lookup(plaintext)
            .flatMap { existing ->
                repository.revokeAllForUser(existing.userId, clock.instant())
                    .map { count -> RevokedAll(userId = existing.userId, count = count) }
            }

    fun revokeAllForUserId(userId: UUID): Mono<Long> =
        repository.revokeAllForUser(userId, clock.instant())

    fun listActiveForUser(userId: UUID): Flux<RefreshToken> =
        repository.findActiveByUser(userId, clock.instant())

    fun revokeByIdForUser(id: UUID, userId: UUID): Mono<Boolean> =
        repository.revokeByIdForUser(id, userId, clock.instant())
            .map { it > 0L }

    private fun lookup(plaintext: String): Mono<RefreshToken> {
        if (plaintext.isBlank()) return Mono.error(AuthException(AuthFailureCode.REFRESH_INVALID))
        return repository.findByTokenHash(hash(plaintext))
            .switchIfEmpty(Mono.error(AuthException(AuthFailureCode.REFRESH_INVALID)))
    }

    private fun handleReplay(existing: RefreshToken, now: Instant): Mono<RotatedRefresh> =
        repository.revokeAllForUser(existing.userId, now)
            .flatMap { revoked ->
                if (revoked > 0L) {
                    log.warn(
                        "Refresh token replay detected for userId={} tokenId={}; revoked {} active sessions",
                        existing.userId, existing.id, revoked,
                    )
                    auditLogger?.refreshReplay(existing.userId, ip = null)
                    Mono.error(AuthException(AuthFailureCode.REFRESH_REPLAY_DETECTED))
                } else {
                    Mono.error(AuthException(AuthFailureCode.REFRESH_REVOKED))
                }
            }

    private fun generatePlaintext(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    private fun hash(plaintext: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(plaintext.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            hex.append(HEX_CHARS[(b.toInt() ushr 4) and 0xF])
            hex.append(HEX_CHARS[b.toInt() and 0xF])
        }
        return hex.toString()
    }

    companion object {
        private const val TOKEN_BYTES = 32
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
        // Match refresh_tokens.user_agent VARCHAR(512) / ip VARCHAR(64) to avoid DB-side truncation errors.
        private const val MAX_USER_AGENT_LENGTH = 512
        private const val MAX_IP_LENGTH = 64
    }
}

data class IssuedRefresh(val plaintext: String, val expiresAt: Instant)
data class RotatedRefresh(val userId: UUID, val refresh: IssuedRefresh)
data class RevokedAll(val userId: UUID, val count: Long)