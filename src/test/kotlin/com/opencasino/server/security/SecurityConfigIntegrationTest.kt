package com.opencasino.server.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.opencasino.server.user.User
import com.opencasino.server.user.UserRepository
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.time.Instant
import java.util.Date
import java.util.UUID

private const val TEST_ISSUER = "opencasino-test"

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///securityconfigtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:securityconfigtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "app.jwt.issuer=$TEST_ISSUER",
    ]
)
@AutoConfigureWebTestClient
@ActiveProfiles("security-on")
class SecurityConfigIntegrationTest {

    @Autowired
    lateinit var keys: JwtKeyMaterial

    @Autowired
    lateinit var jwtIssuer: JwtIssuer

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var users: UserRepository

    @Nested
    inner class HttpRequests {

        @Test
        fun `GET on a protected path without auth returns 401`() {
            webClient.get().uri("/api/anything-not-allowlisted")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `auth me with valid jwt returns 200 and echoes claims`() {
            val userId = UUID.randomUUID()
            users.save(
                User(
                    id = userId,
                    email = "me-${userId}@example.com",
                    displayName = "me-${userId.toString().take(8)}",
                )
            ).block()
            val token = signToken(
                JWTClaimsSet.Builder()
                    .issuer(TEST_ISSUER)
                    .subject(userId.toString())
                    .issueTime(Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                    .claim(JwtIssuer.CLAIM_EMAIL, "me@example.com")
                    .claim(JwtIssuer.CLAIM_ROLES, listOf("USER"))
                    .build(),
                keys.privateKey,
                keys.keyId,
            )

            webClient.get().uri("/auth/me")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.userId").isEqualTo(userId.toString())
                .jsonPath("$.email").isEqualTo("me@example.com")
                .jsonPath("$.displayName").isEqualTo("me-${userId.toString().take(8)}")
                .jsonPath("$.roles[0]").isEqualTo("USER")
        }

        @Test
        fun `auth me without token returns 401`() {
            webClient.get().uri("/auth/me")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `auth me with token signed by foreign key returns 401`() {
            val foreignPair = KeyPairGenerator.getInstance("RSA")
                .apply { initialize(2048) }.generateKeyPair()
            val token = signToken(
                JWTClaimsSet.Builder()
                    .issuer(TEST_ISSUER)
                    .subject(UUID.randomUUID().toString())
                    .issueTime(Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                    .build(),
                foreignPair.private as RSAPrivateKey,
                keys.keyId,
            )

            webClient.get().uri("/auth/me")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `auth me with expired token returns 401`() {
            val expired = signToken(
                JWTClaimsSet.Builder()
                    .issuer(TEST_ISSUER)
                    .subject(UUID.randomUUID().toString())
                    .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                    .expirationTime(Date.from(Instant.now().minusSeconds(60)))
                    .build(),
                keys.privateKey,
                keys.keyId,
            )

            webClient.get().uri("/auth/me")
                .header("Authorization", "Bearer $expired")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `auth me with wrong issuer returns 401`() {
            val token = signToken(
                JWTClaimsSet.Builder()
                    .issuer("someone-else")
                    .subject(UUID.randomUUID().toString())
                    .issueTime(Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                    .build(),
                keys.privateKey,
                keys.keyId,
            )

            webClient.get().uri("/auth/me")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isUnauthorized
        }
    }

    private fun signToken(
        claims: JWTClaimsSet,
        signingKey: RSAPrivateKey,
        kid: String,
    ): String {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .type(JOSEObjectType.JWT)
            .keyID(kid)
            .build()
        return SignedJWT(header, claims).apply { sign(RSASSASigner(signingKey)) }.serialize()
    }

    @Nested
    inner class WebSocketHandshake {

        @Test
        fun `handshake on ws without auth returns 401`() {
            webClient.get().uri("/ws")
                .header("Upgrade", "websocket")
                .header("Connection", "Upgrade")
                .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
                .header("Sec-WebSocket-Version", "13")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `handshake on ws menu without auth is permitted`() {
            webClient.get().uri("/ws/menu")
                .header("Upgrade", "websocket")
                .header("Connection", "Upgrade")
                .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
                .header("Sec-WebSocket-Version", "13")
                .exchange()
                .expectStatus().value { code -> assertNotEquals(401, code) }
        }
    }
}
