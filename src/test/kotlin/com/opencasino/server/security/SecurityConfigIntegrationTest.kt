package com.opencasino.server.security

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///securityconfigtest;DB_CLOSE_DELAY=-1",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
    ]
)
@AutoConfigureWebTestClient
@ActiveProfiles("security-on")
class SecurityConfigIntegrationTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Nested
    inner class HttpRequests {

        @Test
        fun `GET on a protected path without auth returns 401`() {
            webClient.get().uri("/api/anything-not-allowlisted")
                .exchange()
                .expectStatus().isUnauthorized
        }
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
    }
}
