@file:Suppress("JsonStandardCompliance")

package com.opencasino.server.security

import com.opencasino.server.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///authcontrollertest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:authcontrollertest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "app.jwt.issuer=opencasino-test",
    ]
)
@AutoConfigureWebTestClient
@ActiveProfiles("security-on")
class AuthControllerIntegrationTest {

    @Autowired lateinit var webClient: WebTestClient
    @Autowired lateinit var users: UserRepository

    private fun freshEmail(prefix: String) = "$prefix-${UUID.randomUUID()}@example.com"

    @Test
    fun `register creates a user with password hash and returns 201`() {
        val email = freshEmail("alice")

        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "password" to "correct-horse-battery"))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.userId").exists()
            .jsonPath("$.email").isEqualTo(email)

        val saved = users.findByEmail(email).block()
        assertNotNull(saved)
        assertTrue(saved!!.passwordHash!!.startsWith("{bcrypt}"))
    }

    @Test
    fun `duplicate registration returns 409`() {
        val email = freshEmail("dup")
        val body = mapOf("email" to email, "password" to "correct-horse-battery")

        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
            .exchange().expectStatus().isCreated

        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo("EMAIL_TAKEN")
    }

    @Test
    fun `weak password is rejected with 400`() {
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to freshEmail("weak"), "password" to "short"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("WEAK_PASSWORD")
    }

    @Test
    fun `invalid email format is rejected with 400`() {
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to "not-an-email", "password" to "correct-horse-battery"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_EMAIL")
    }

    @Test
    fun `login returns 200 with signed JWT after successful register`() {
        val email = freshEmail("loginok")
        val password = "correct-horse-battery"

        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange().expectStatus().isCreated

        webClient.post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accessToken").value<String> {
                assertTrue(it.split(".").size == 3, "accessToken should be a JWT (three segments)")
            }
            .jsonPath("$.refreshToken").value<String> { assertTrue(it.startsWith("stub-refresh-")) }
            .jsonPath("$.tokenType").isEqualTo("Bearer")
            .jsonPath("$.expiresAt").exists()
            .jsonPath("$.userId").exists()
    }

    @Test
    fun `login with wrong password returns 401`() {
        val email = freshEmail("wrongpw")
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "password" to "correct-horse-battery"))
            .exchange().expectStatus().isCreated

        webClient.post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "password" to "incorrect-horse"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_CREDENTIALS")
    }

    @Test
    fun `login with non-existent email returns 401`() {
        webClient.post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to freshEmail("ghost"), "password" to "correct-horse-battery"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_CREDENTIALS")
    }

    @Test
    fun `email is normalized to lowercase on register and login`() {
        val mixedCase = "MiXeD-${UUID.randomUUID()}@example.com"
        val lower = mixedCase.lowercase()

        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to mixedCase, "password" to "correct-horse-battery"))
            .exchange().expectStatus().isCreated

        assertNotNull(users.findByEmail(lower).block())
        assertNull(users.findByEmail(mixedCase).block())

        webClient.post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to mixedCase, "password" to "correct-horse-battery"))
            .exchange().expectStatus().isOk
    }

    @Test
    fun `malformed JSON body returns 400`() {
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("not json at all")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("MALFORMED_REQUEST")
    }
}