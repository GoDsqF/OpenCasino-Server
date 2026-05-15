package com.opencasino.server.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import com.opencasino.server.user.Role
import com.opencasino.server.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class JwtIssuerTest {

    private val now = Instant.parse("2026-05-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val keys = generateKeys()
    private val props = JwtProperties(
        issuer = "opencasino-test",
        accessTtl = Duration.ofMinutes(15),
        keyId = "test-key",
    )
    private val issuer = JwtIssuer(props, keys, clock)

    @Test
    fun `issues RS256 jwt with expected claims`() {
        val user = sampleUser()
        val (serialized, expiresAt) = issuer.issueAccess(user)

        val parsed = SignedJWT.parse(serialized)
        val claims = parsed.jwtClaimsSet

        assertEquals(JWSAlgorithm.RS256, parsed.header.algorithm)
        assertEquals("test-key", parsed.header.keyID)
        assertEquals("opencasino-test", claims.issuer)
        assertEquals(user.id.toString(), claims.subject)
        assertEquals(user.email, claims.getStringClaim("email"))
        assertEquals(listOf("USER"), claims.getStringListClaim("roles"))
        assertEquals(now, claims.issueTime.toInstant())
        assertEquals(now.plus(Duration.ofMinutes(15)), claims.expirationTime.toInstant())
        assertEquals(now.plus(Duration.ofMinutes(15)), expiresAt)
    }

    @Test
    fun `signature verifies with corresponding public key`() {
        val user = sampleUser()
        val (serialized, _) = issuer.issueAccess(user)
        val parsed = SignedJWT.parse(serialized)

        assertTrue(parsed.verify(RSASSAVerifier(keys.publicKey)))
    }

    @Test
    fun `tampered payload fails signature verification`() {
        val user = sampleUser()
        val (serialized, _) = issuer.issueAccess(user)
        val parts = serialized.split(".")
        val tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"attacker","iss":"opencasino-test"}""".toByteArray()
        )
        val tampered = "${parts[0]}.$tamperedPayload.${parts[2]}"

        val parsed = SignedJWT.parse(tampered)
        assertEquals(false, parsed.verify(RSASSAVerifier(keys.publicKey)))
    }

    private fun sampleUser() = User(
        id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        email = "alice@example.com",
        displayName = "alice",
        role = Role.USER,
    )

    private fun generateKeys(): JwtKeyMaterial {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        return JwtKeyMaterial(
            privateKey = pair.private as RSAPrivateKey,
            publicKey = pair.public as RSAPublicKey,
            keyId = "test-key",
        )
    }
}