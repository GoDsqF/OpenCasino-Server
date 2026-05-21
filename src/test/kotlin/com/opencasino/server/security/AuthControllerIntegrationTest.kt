@file:Suppress("JsonStandardCompliance")

package com.opencasino.server.security

import com.opencasino.server.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.security.MessageDigest
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
        "app.auth.displayNameBlocklist=admin,root,moderator",
        "app.ratelimit.enabled=false",
        // Trust loopback only — WebTestClient either connects from 127.0.0.1 or is
        // in-process (null remoteAddress, which the resolver also treats as trusted).
        // The real client IP (set via XFF below) is NOT trusted, so the walk surfaces it.
        "app.security.trusted-proxies=127.0.0.0/8,::1/128",
        "spring.test.webtestclient.timeout=30s",
    ]
)
@AutoConfigureWebTestClient
@ActiveProfiles("security-on")
class AuthControllerIntegrationTest {

    @Autowired lateinit var webClient: WebTestClient
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var refreshTokens: RefreshTokenRepository

    private fun freshEmail(prefix: String) = "$prefix-${UUID.randomUUID()}@example.com"
    private fun freshName(prefix: String) = "$prefix${UUID.randomUUID().toString().take(8).replace("-", "")}"

    private fun registerBody(email: String, password: String = "correct-horse-battery", displayName: String? = freshName("u")) =
        buildMap<String, Any> {
            put("email", email)
            put("password", password)
            if (displayName != null) put("displayName", displayName)
        }

    @Test
    fun `register creates a user with password hash and returns 201`() {
        val email = freshEmail("alice")
        val displayName = freshName("alice")

        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(email, displayName = displayName))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.userId").exists()
            .jsonPath("$.email").isEqualTo(email)
            .jsonPath("$.displayName").isEqualTo(displayName)

        val saved = users.findByEmail(email).block()
        assertNotNull(saved)
        assertEquals(displayName, saved!!.displayName)
        assertTrue(saved.passwordHash!!.startsWith("{bcrypt}"))
    }

    @Test
    fun `duplicate registration returns 409`() {
        val email = freshEmail("dup")

        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(registerBody(email))
            .exchange().expectStatus().isCreated

        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(registerBody(email))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo("EMAIL_TAKEN")
    }

    @Test
    fun `weak password is rejected with 400`() {
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(freshEmail("weak"), password = "short"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("WEAK_PASSWORD")
    }

    @Test
    fun `invalid email format is rejected with 400`() {
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody("not-an-email"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_EMAIL")
    }

    @Test
    fun `missing displayName is rejected with 400`() {
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(freshEmail("noname"), displayName = null))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_DISPLAY_NAME")
    }

    @Test
    fun `displayName with denylisted substring is rejected with 400`() {
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(freshEmail("evil"), displayName = "superadmin42"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_DISPLAY_NAME")
    }

    @Test
    fun `displayName with illegal characters is rejected with 400`() {
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(freshEmail("bad"), displayName = "spaces are bad"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_DISPLAY_NAME")
    }

    @Test
    fun `login returns 200 with signed JWT after successful register`() {
        val email = freshEmail("loginok")
        val password = "correct-horse-battery"

        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(email, password))
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
            .jsonPath("$.refreshToken").value<String> {
                assertTrue(it.isNotBlank() && it.length >= 32, "refreshToken should look like a high-entropy random: got $it")
            }
            .jsonPath("$.refreshExpiresAt").exists()
            .jsonPath("$.tokenType").isEqualTo("Bearer")
            .jsonPath("$.expiresAt").exists()
            .jsonPath("$.userId").exists()
    }

    @Test
    fun `auth me returns user balance from DB`() {
        val pair = registerAndLogin("mebalance")
        val access = pair["accessToken"] as String

        webClient.get().uri("/auth/me")
            .header("Authorization", "Bearer $access")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").exists()
            .jsonPath("$.balance").isEqualTo(0.0)
    }

    private fun registerAndLogin(prefix: String): Map<String, Any> {
        val email = freshEmail(prefix)
        val password = "correct-horse-battery"
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(email, password))
            .exchange().expectStatus().isCreated

        @Suppress("UNCHECKED_CAST")
        return webClient.post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange()
            .expectStatus().isOk
            .returnResult(Map::class.java)
            .responseBody.blockFirst() as Map<String, Any>
    }

    @Test
    fun `refresh rotates the token and returns a new pair`() {
        val first = registerAndLogin("refresh1")
        val firstRefresh = first["refreshToken"] as String
        val firstAccess = first["accessToken"] as String

        val second = webClient.post().uri("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to firstRefresh))
            .exchange()
            .expectStatus().isOk
            .returnResult(Map::class.java)
            .responseBody.blockFirst()!!

        val secondRefresh = second["refreshToken"] as String
        val secondAccess = second["accessToken"] as String
        assertTrue(secondRefresh != firstRefresh, "refresh should rotate")
        assertTrue(secondAccess != firstAccess || (secondAccess).split(".").size == 3)
    }

    @Test
    fun `using refresh after rotation triggers replay detection and revokes all sessions`() {
        val first = registerAndLogin("replay1")
        val firstRefresh = first["refreshToken"] as String

        val rotated = webClient.post().uri("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to firstRefresh))
            .exchange()
            .expectStatus().isOk
            .returnResult(Map::class.java)
            .responseBody.blockFirst()!!

        webClient.post().uri("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to firstRefresh))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("REFRESH_REPLAY_DETECTED")

        webClient.post().uri("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to rotated["refreshToken"] as String))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("REFRESH_REVOKED")
    }

    @Test
    fun `unknown refresh token is rejected with REFRESH_INVALID`() {
        webClient.post().uri("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to "definitely-not-a-real-token"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("REFRESH_INVALID")
    }

    @Test
    fun `logout revokes the refresh token`() {
        val pair = registerAndLogin("logout1")
        val refresh = pair["refreshToken"] as String

        webClient.post().uri("/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to refresh))
            .exchange()
            .expectStatus().isNoContent

        webClient.post().uri("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to refresh))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("REFRESH_REVOKED")
    }

    @Test
    fun `access token remains valid after logout until its own TTL expires`() {
        val pair = registerAndLogin("logoutaccess")
        val refresh = pair["refreshToken"] as String
        val access = pair["accessToken"] as String

        webClient.post().uri("/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to refresh))
            .exchange()
            .expectStatus().isNoContent

        webClient.get().uri("/auth/me")
            .header("Authorization", "Bearer $access")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `login with wrong password returns 401`() {
        val email = freshEmail("wrongpw")
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(email))
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
            .bodyValue(registerBody(mixedCase))
            .exchange().expectStatus().isCreated

        assertNotNull(users.findByEmail(lower).block())
        assertNull(users.findByEmail(mixedCase).block())

        webClient.post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to mixedCase, "password" to "correct-horse-battery"))
            .exchange().expectStatus().isOk
    }

    @Test
    fun `login persists user_agent and ip on the refresh_tokens row`() {
        val email = freshEmail("uatrack")
        val password = "correct-horse-battery"
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(email, password))
            .exchange().expectStatus().isCreated

        @Suppress("UNCHECKED_CAST")
        val loginResponse = webClient.post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.USER_AGENT, "phase8-test-agent/1.0")
            .header("X-Forwarded-For", "203.0.113.7")
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange().expectStatus().isOk
            .returnResult(Map::class.java).responseBody.blockFirst() as Map<String, Any>

        val plaintext = loginResponse["refreshToken"] as String
        val token = refreshTokens.findByTokenHash(sha256Hex(plaintext)).block()
        assertNotNull(token)
        assertEquals("phase8-test-agent/1.0", token!!.userAgent)
        assertEquals("203.0.113.7", token.ip)
    }

    private fun sha256Hex(plaintext: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(plaintext.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `logout all revokes every active refresh token for the user`() {
        val email = freshEmail("logoutall")
        val password = "correct-horse-battery"
        webClient.post().uri("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(email, password))
            .exchange().expectStatus().isCreated

        val session1 = login(email, password)
        val session2 = login(email, password)

        webClient.post().uri("/auth/logout?all=true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to (session1["refreshToken"] as String)))
            .exchange()
            .expectStatus().isNoContent

        webClient.post().uri("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to (session1["refreshToken"] as String)))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("REFRESH_REVOKED")

        webClient.post().uri("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to (session2["refreshToken"] as String)))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("REFRESH_REVOKED")
    }

    @Test
    fun `logout all without refresh token returns 401 REFRESH_INVALID`() {
        webClient.post().uri("/auth/logout?all=true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf<String, Any>())
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("REFRESH_INVALID")
    }

    @Test
    fun `GET sessions lists active sessions for the authenticated user only`() {
        val emailA = freshEmail("sessA")
        val emailB = freshEmail("sessB")
        val password = "correct-horse-battery"

        webClient.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(emailA, password)).exchange().expectStatus().isCreated
        webClient.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(emailB, password)).exchange().expectStatus().isCreated

        val a1 = login(emailA, password, userAgent = "UA-A1", xff = "203.0.113.10")
        login(emailA, password, userAgent = "UA-A2", xff = "203.0.113.11")
        login(emailB, password, userAgent = "UA-B1", xff = "203.0.113.20")

        @Suppress("UNCHECKED_CAST")
        val rows = webClient.get().uri("/auth/sessions")
            .header("Authorization", "Bearer ${a1["accessToken"]}")
            .exchange()
            .expectStatus().isOk
            .returnResult(List::class.java)
            .responseBody.blockFirst() as List<Map<String, Any>>

        assertEquals(2, rows.size, "user A should see exactly its two active sessions")
        val agents = rows.mapNotNull { it["userAgent"] as String? }.toSet()
        assertEquals(setOf("UA-A1", "UA-A2"), agents)
        rows.forEach {
            assertNotNull(it["id"])
            assertNotNull(it["createdAt"])
            assertNotNull(it["expiresAt"])
        }
    }

    @Test
    fun `GET sessions hides revoked rows`() {
        val email = freshEmail("seshrev")
        val password = "correct-horse-battery"
        webClient.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(email, password)).exchange().expectStatus().isCreated

        val active = login(email, password, userAgent = "still-here")
        val toRevoke = login(email, password, userAgent = "going-away")

        webClient.post().uri("/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to (toRevoke["refreshToken"] as String)))
            .exchange().expectStatus().isNoContent

        @Suppress("UNCHECKED_CAST")
        val rows = webClient.get().uri("/auth/sessions")
            .header("Authorization", "Bearer ${active["accessToken"]}")
            .exchange()
            .expectStatus().isOk
            .returnResult(List::class.java)
            .responseBody.blockFirst() as List<Map<String, Any>>

        assertEquals(1, rows.size)
        assertEquals("still-here", rows.single()["userAgent"])
    }

    @Test
    fun `GET sessions without auth returns 401`() {
        webClient.get().uri("/auth/sessions")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `DELETE sessions revokes own row and 404 for other users`() {
        val emailA = freshEmail("delA")
        val emailB = freshEmail("delB")
        val password = "correct-horse-battery"
        webClient.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(emailA, password)).exchange().expectStatus().isCreated
        webClient.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerBody(emailB, password)).exchange().expectStatus().isCreated

        val a1 = login(emailA, password)
        val a2 = login(emailA, password)
        val b1 = login(emailB, password)

        @Suppress("UNCHECKED_CAST")
        val sessions = webClient.get().uri("/auth/sessions")
            .header("Authorization", "Bearer ${a1["accessToken"]}")
            .exchange().expectStatus().isOk
            .returnResult(List::class.java)
            .responseBody.blockFirst() as List<Map<String, Any>>

        val targetId = sessions.first()["id"] as String

        // User A can revoke own session.
        webClient.delete().uri("/auth/sessions/$targetId")
            .header("Authorization", "Bearer ${a1["accessToken"]}")
            .exchange().expectStatus().isNoContent

        // After revoke, list shows only the other session.
        @Suppress("UNCHECKED_CAST")
        val remaining = webClient.get().uri("/auth/sessions")
            .header("Authorization", "Bearer ${a2["accessToken"]}")
            .exchange().expectStatus().isOk
            .returnResult(List::class.java)
            .responseBody.blockFirst() as List<Map<String, Any>>
        assertEquals(1, remaining.size)
        assertTrue(remaining.none { (it["id"] as String) == targetId })

        // Second revoke on the same id (already revoked) returns 404.
        webClient.delete().uri("/auth/sessions/$targetId")
            .header("Authorization", "Bearer ${a1["accessToken"]}")
            .exchange().expectStatus().isNotFound

        // User B cannot revoke A's session (even one that was active for A).
        @Suppress("UNCHECKED_CAST")
        val aRows = webClient.get().uri("/auth/sessions")
            .header("Authorization", "Bearer ${a2["accessToken"]}")
            .exchange().expectStatus().isOk
            .returnResult(List::class.java)
            .responseBody.blockFirst() as List<Map<String, Any>>
        val aSessionId = aRows.single()["id"] as String

        webClient.delete().uri("/auth/sessions/$aSessionId")
            .header("Authorization", "Bearer ${b1["accessToken"]}")
            .exchange().expectStatus().isNotFound

        // A's session is still alive — B's attempt did not affect it.
        @Suppress("UNCHECKED_CAST")
        val aStill = webClient.get().uri("/auth/sessions")
            .header("Authorization", "Bearer ${a2["accessToken"]}")
            .exchange().expectStatus().isOk
            .returnResult(List::class.java)
            .responseBody.blockFirst() as List<Map<String, Any>>
        assertEquals(1, aStill.size)
    }

    private fun login(
        email: String,
        password: String,
        userAgent: String? = null,
        xff: String? = null,
    ): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return webClient.post().uri("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .apply {
                userAgent?.let { header(HttpHeaders.USER_AGENT, it) }
                xff?.let { header("X-Forwarded-For", it) }
            }
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange()
            .expectStatus().isOk
            .returnResult(Map::class.java)
            .responseBody.blockFirst() as Map<String, Any>
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
