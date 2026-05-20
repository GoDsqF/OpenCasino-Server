@file:Suppress("JsonStandardCompliance")

package com.opencasino.server.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///corsintegrationtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:corsintegrationtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "app.jwt.issuer=opencasino-test",
        "app.cors.allowed-origins=https://app.example.com,http://localhost:3000",
        "app.ratelimit.enabled=false",
    ]
)
@ActiveProfiles("security-on")
class CorsIntegrationTest {

    // Bind WebTestClient to the real port — @AutoConfigureWebTestClient gives an
    // in-process client whose request URI lacks scheme/host, which trips Spring's
    // CorsUtils.isSameOrigin (null scheme assertion) and shows up as
    // "Reject: origin is malformed" 403. Real HTTP avoids that path.
    @LocalServerPort var port: Int = 0
    private lateinit var webClient: WebTestClient

    @BeforeEach
    fun setUp() {
        webClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(30))
            .build()
    }

    @Test
    fun `preflight from allowed origin returns Access-Control-Allow-Origin`() {
        webClient.options().uri("/auth/login")
            .header(HttpHeaders.ORIGIN, "https://app.example.com")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.com")
            .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
    }

    @Test
    fun `preflight from disallowed origin is rejected`() {
        webClient.options().uri("/auth/login")
            .header(HttpHeaders.ORIGIN, "https://evil.example.com")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `non-cors request to an allowed permit-all endpoint passes through`() {
        webClient.post().uri("/auth/login")
            .bodyValue(mapOf("email" to "", "password" to ""))
            .exchange()
            .expectStatus().isUnauthorized
    }
}
