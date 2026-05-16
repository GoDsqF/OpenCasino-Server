package com.opencasino.server.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Mono
import java.net.URI
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID

private const val TEST_ISSUER = "opencasino-test"

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///wshandshaketest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:wshandshaketest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "app.jwt.issuer=$TEST_ISSUER",
    ]
)
@ActiveProfiles("security-on")
class WebSocketHandshakeAuthTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var keys: JwtKeyMaterial

    private val client = ReactorNettyWebSocketClient()

    private fun wsUri(query: String = ""): URI = URI("ws://localhost:$port/ws$query")

    private fun openWith(
        headers: HttpHeaders = HttpHeaders(),
        query: String = "",
        offeredSubProtocols: List<String> = emptyList(),
    ): Throwable? {
        val handler = object : WebSocketHandler {
            override fun handle(session: WebSocketSession): Mono<Void> = session.send(Mono.empty()).then()
            override fun getSubProtocols(): List<String> = offeredSubProtocols
        }
        return runCatching {
            client.execute(wsUri(query), headers, handler).block(Duration.ofSeconds(5))
        }.exceptionOrNull()
    }

    private fun validToken(): String = signToken(
        JWTClaimsSet.Builder()
            .issuer(TEST_ISSUER)
            .subject(UUID.randomUUID().toString())
            .issueTime(Date())
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .claim(JwtIssuer.CLAIM_EMAIL, "ws@example.com")
            .claim(JwtIssuer.CLAIM_ROLES, listOf("USER"))
            .build(),
        keys.privateKey,
        keys.keyId,
    )

    private fun signToken(claims: JWTClaimsSet, key: RSAPrivateKey, kid: String): String =
        SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(kid).build(),
            claims,
        ).apply { sign(RSASSASigner(key)) }.serialize()

    @Test
    fun `handshake without token is rejected`() {
        val error = openWith()
        assert(error != null) { "expected the handshake to fail with no token" }
    }

    @Test
    fun `handshake with forged token is rejected`() {
        val foreign = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val forged = signToken(
            JWTClaimsSet.Builder()
                .issuer(TEST_ISSUER)
                .subject(UUID.randomUUID().toString())
                .issueTime(Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build(),
            foreign.private as RSAPrivateKey,
            keys.keyId,
        )
        val error = openWith(query = "?token=$forged")
        assert(error != null) { "expected the handshake to fail with a forged token" }
    }

    @Test
    fun `handshake with valid token in query string succeeds`() {
        val error = openWith(query = "?token=${validToken()}")
        assertEquals(null, error)
    }

    @Test
    fun `handshake with valid token in sub-protocol succeeds and echoes bearer only`() {
        val token = validToken()
        val error = openWith(offeredSubProtocols = listOf("bearer", token))
        assertEquals(null, error)
    }

    @Test
    fun `handshake with expired token in query string is rejected`() {
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
        val error = openWith(query = "?token=$expired")
        assert(error != null) { "expected the handshake to fail with an expired token" }
    }
}
