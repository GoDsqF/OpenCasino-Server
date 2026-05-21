@file:Suppress("JsonStandardCompliance")

package com.opencasino.server.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///ratelimittest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:ratelimittest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "app.jwt.issuer=opencasino-test",
        "app.ratelimit.enabled=true",
        // shrink so a few calls saturate
        "app.ratelimit.login.capacity=3",
        "app.ratelimit.login.refill=3",
        "app.ratelimit.login.period=PT1M",
        "spring.test.webtestclient.timeout=30s",
    ]
)
@AutoConfigureWebTestClient
@ActiveProfiles("security-on")
class RateLimitIntegrationTest {

    @Autowired lateinit var webClient: WebTestClient

    @Test
    fun `login bucket returns 429 with Retry-After header after capacity is exhausted`() {
        val body = mapOf("email" to "noone@example.com", "password" to "irrelevant1")
        repeat(3) {
            webClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isUnauthorized
        }

        webClient.post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().exists(HttpHeaders.RETRY_AFTER)
    }
}
