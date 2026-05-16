package com.opencasino.server.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RefreshTokenServiceTest {

    private val now = Instant.parse("2026-05-16T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val props = JwtProperties(
        issuer = "opencasino-test",
        accessTtl = Duration.ofMinutes(15),
        refreshTtl = Duration.ofDays(30),
    )
    private val random = SecureRandom().apply { setSeed(42L) }

    private lateinit var repository: RefreshTokenRepository
    private lateinit var service: RefreshTokenService

    @BeforeEach
    fun setUp() {
        repository = mock()
        service = RefreshTokenService(repository, props, clock, random)
    }

    @Test
    fun `issue persists hashed token with TTL from props`() {
        val userId = UUID.randomUUID()
        whenever(repository.save(any())).thenAnswer { Mono.just(it.arguments[0] as RefreshToken) }

        val issued = service.issue(userId).block()!!

        assertEquals(now.plus(Duration.ofDays(30)), issued.expiresAt)
        assertTrue(issued.plaintext.isNotBlank())

        val captor = argumentCaptor<RefreshToken>()
        verify(repository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals(userId, saved.userId)
        assertEquals(now, saved.createdAt)
        assertEquals(now.plus(Duration.ofDays(30)), saved.expiresAt)
        assertEquals(sha256Hex(issued.plaintext), saved.tokenHash)
        assertNotEquals(issued.plaintext, saved.tokenHash)
    }

    @Test
    fun `rotate marks old token revoked and issues a new one`() {
        val userId = UUID.randomUUID()
        val plaintext = "active-token"
        val existing = RefreshToken(
            userId = userId,
            tokenHash = sha256Hex(plaintext),
            createdAt = now.minus(Duration.ofDays(1)),
            expiresAt = now.plus(Duration.ofDays(29)),
        )
        whenever(repository.findByTokenHash(sha256Hex(plaintext))).thenReturn(Mono.just(existing))
        whenever(repository.markRevoked(eq(existing.id), eq(now))).thenReturn(Mono.just(1L))
        whenever(repository.save(any())).thenAnswer { Mono.just(it.arguments[0] as RefreshToken) }

        val rotated = service.rotate(plaintext).block()!!

        assertEquals(userId, rotated.userId)
        assertNotEquals(plaintext, rotated.refresh.plaintext)
        verify(repository).markRevoked(eq(existing.id), eq(now))
    }

    @Test
    fun `rotate rejects expired refresh token`() {
        val plaintext = "expired-token"
        val existing = RefreshToken(
            userId = UUID.randomUUID(),
            tokenHash = sha256Hex(plaintext),
            createdAt = now.minus(Duration.ofDays(40)),
            expiresAt = now.minus(Duration.ofMinutes(1)),
        )
        whenever(repository.findByTokenHash(sha256Hex(plaintext))).thenReturn(Mono.just(existing))

        StepVerifier.create(service.rotate(plaintext))
            .verifyErrorMatches { it is AuthException && it.failure == AuthFailureCode.REFRESH_EXPIRED }
    }

    @Test
    fun `replay of revoked token with other active sessions returns REPLAY_DETECTED and revokes all`() {
        val userId = UUID.randomUUID()
        val plaintext = "leaked-token"
        val existing = RefreshToken(
            userId = userId,
            tokenHash = sha256Hex(plaintext),
            createdAt = now.minus(Duration.ofMinutes(10)),
            expiresAt = now.plus(Duration.ofDays(30)),
            revokedAt = now.minus(Duration.ofMinutes(5)),
        )
        whenever(repository.findByTokenHash(sha256Hex(plaintext))).thenReturn(Mono.just(existing))
        whenever(repository.revokeAllForUser(eq(userId), eq(now))).thenReturn(Mono.just(2L))

        StepVerifier.create(service.rotate(plaintext))
            .verifyErrorMatches { it is AuthException && it.failure == AuthFailureCode.REFRESH_REPLAY_DETECTED }

        verify(repository).revokeAllForUser(eq(userId), eq(now))
    }

    @Test
    fun `revoked token with no active sessions returns REVOKED, not REPLAY`() {
        val userId = UUID.randomUUID()
        val plaintext = "loggedout-token"
        val existing = RefreshToken(
            userId = userId,
            tokenHash = sha256Hex(plaintext),
            createdAt = now.minus(Duration.ofMinutes(10)),
            expiresAt = now.plus(Duration.ofDays(30)),
            revokedAt = now.minus(Duration.ofSeconds(1)),
        )
        whenever(repository.findByTokenHash(sha256Hex(plaintext))).thenReturn(Mono.just(existing))
        whenever(repository.revokeAllForUser(eq(userId), eq(now))).thenReturn(Mono.just(0L))

        StepVerifier.create(service.rotate(plaintext))
            .verifyErrorMatches { it is AuthException && it.failure == AuthFailureCode.REFRESH_REVOKED }
    }

    @Test
    fun `unknown token returns REFRESH_INVALID`() {
        whenever(repository.findByTokenHash(any())).thenReturn(Mono.empty())

        StepVerifier.create(service.rotate("nonexistent"))
            .verifyErrorMatches { it is AuthException && it.failure == AuthFailureCode.REFRESH_INVALID }
    }

    @Test
    fun `blank token returns REFRESH_INVALID without DB lookup`() {
        StepVerifier.create(service.rotate("  "))
            .verifyErrorMatches { it is AuthException && it.failure == AuthFailureCode.REFRESH_INVALID }
    }

    @Test
    fun `revoke marks token revoked and is idempotent on already-revoked`() {
        val plaintext = "to-revoke"
        val active = RefreshToken(
            userId = UUID.randomUUID(),
            tokenHash = sha256Hex(plaintext),
            createdAt = now.minus(Duration.ofMinutes(1)),
            expiresAt = now.plus(Duration.ofDays(30)),
        )
        whenever(repository.findByTokenHash(sha256Hex(plaintext))).thenReturn(Mono.just(active))
        whenever(repository.markRevoked(eq(active.id), eq(now))).thenReturn(Mono.just(1L))

        StepVerifier.create(service.revoke(plaintext)).verifyComplete()

        verify(repository).markRevoked(eq(active.id), eq(now))

        val already = active.copy(revokedAt = now.minus(Duration.ofSeconds(5)))
        whenever(repository.findByTokenHash(sha256Hex("revoked-already"))).thenReturn(Mono.just(already))

        StepVerifier.create(service.revoke("revoked-already")).verifyComplete()
    }

    private fun sha256Hex(plaintext: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(plaintext.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

}