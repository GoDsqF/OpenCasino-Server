package com.opencasino.server.security

import com.opencasino.server.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant

class OAuth2LoginSuccessHandlerTest {

    private lateinit var linking: OAuth2UserLinkingService
    private lateinit var jwtIssuer: JwtIssuer
    private lateinit var refreshTokenService: RefreshTokenService
    private lateinit var props: AuthProperties
    private lateinit var clientIpResolver: ClientIpResolver
    private lateinit var auditLogger: SecurityAuditLogger
    private lateinit var handler: OAuth2LoginSuccessHandler

    @BeforeEach
    fun setUp() {
        linking = mock()
        jwtIssuer = mock()
        refreshTokenService = mock()
        clientIpResolver = ClientIpResolver(SecurityNetworkProperties())
        auditLogger = SecurityAuditLogger()
        whenever(refreshTokenService.issue(any(), anyOrNull(), anyOrNull())).thenAnswer {
            Mono.just(IssuedRefresh(plaintext = "refresh-plain", expiresAt = Instant.parse("2026-06-15T12:00:00Z")))
        }
        props = AuthProperties(
            oauth2 = OAuth2RedirectProperties(
                successRedirect = "https://app.example.com/oauth2/redirect",
                failureRedirect = "https://app.example.com/oauth2/error",
            ),
        )
        handler = OAuth2LoginSuccessHandler(linking, jwtIssuer, refreshTokenService, props, clientIpResolver, auditLogger)
    }

    @Test
    fun `successful link redirects to success URI with token and expiresAt`() {
        val user = User(email = "alice@example.com", displayName = "alice")
        val expiresAt = Instant.parse("2026-05-16T12:00:00Z")
        whenever(linking.linkOrCreate(any())).thenReturn(Mono.just(user))
        whenever(jwtIssuer.issueAccess(user)).thenReturn(IssuedToken("jwt-access", expiresAt))

        val exchange = exchangeWithSession()
        val auth = oauth2Token("alice@example.com", emailVerified = true, name = "Alice Smith")

        StepVerifier.create(handler.onAuthenticationSuccess(WebFilterExchange(exchange, noopChain), auth))
            .verifyComplete()

        assertEquals(HttpStatus.FOUND, exchange.response.statusCode)
        val location = exchange.response.headers.location.toString()
        assertTrue(location.startsWith("https://app.example.com/oauth2/redirect"), "got $location")
        assertTrue(location.contains("token=jwt-access"), "got $location")
        assertTrue(location.contains("expiresAt="), "got $location")
        assertTrue(location.contains("refresh=refresh-plain"), "got $location")
        assertTrue(location.contains("refreshExpiresAt="), "got $location")
    }

    @Test
    fun `linking AuthException routes to failure URI with error code`() {
        whenever(linking.linkOrCreate(any()))
            .thenReturn(Mono.error(AuthException(AuthFailureCode.OAUTH_EMAIL_UNVERIFIED)))

        val exchange = exchangeWithSession()
        val auth = oauth2Token("alice@example.com", emailVerified = false, name = "Alice")

        StepVerifier.create(handler.onAuthenticationSuccess(WebFilterExchange(exchange, noopChain), auth))
            .verifyComplete()

        assertEquals(HttpStatus.FOUND, exchange.response.statusCode)
        val location = exchange.response.headers.location.toString()
        assertTrue(location.startsWith("https://app.example.com/oauth2/error"), "got $location")
        assertTrue(location.contains("error=OAUTH_EMAIL_UNVERIFIED"), "got $location")
    }

    @Test
    fun `unexpected error maps to OAUTH_PROVIDER_ERROR`() {
        whenever(linking.linkOrCreate(any())).thenReturn(Mono.error(IllegalStateException("kaboom")))

        val exchange = exchangeWithSession()
        val auth = oauth2Token("alice@example.com", emailVerified = true, name = "Alice")

        StepVerifier.create(handler.onAuthenticationSuccess(WebFilterExchange(exchange, noopChain), auth))
            .verifyComplete()

        val location = exchange.response.headers.location.toString()
        assertTrue(location.contains("error=OAUTH_PROVIDER_ERROR"), "got $location")
    }

    @Test
    fun `failure redirect falls back to success URI when failureRedirect is unset`() {
        val noFailureProps = AuthProperties(
            oauth2 = OAuth2RedirectProperties(
                successRedirect = "https://app.example.com/oauth2/redirect",
                failureRedirect = null,
            ),
        )
        val handlerNoFallback = OAuth2LoginSuccessHandler(linking, jwtIssuer, refreshTokenService, noFailureProps, clientIpResolver, auditLogger)
        whenever(linking.linkOrCreate(any()))
            .thenReturn(Mono.error(AuthException(AuthFailureCode.OAUTH_EMAIL_UNVERIFIED)))

        val exchange = exchangeWithSession()
        val auth = oauth2Token("alice@example.com", emailVerified = false, name = "Alice")

        StepVerifier.create(handlerNoFallback.onAuthenticationSuccess(WebFilterExchange(exchange, noopChain), auth))
            .verifyComplete()

        val location = exchange.response.headers.location.toString()
        assertTrue(location.startsWith("https://app.example.com/oauth2/redirect"), "got $location")
        assertTrue(location.contains("error=OAUTH_EMAIL_UNVERIFIED"), "got $location")
    }

    private fun exchangeWithSession(): MockServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/login/oauth2/code/google").build())

    private val noopChain = WebFilterChain { Mono.empty() }

    private fun oauth2Token(email: String, emailVerified: Boolean, name: String): OAuth2AuthenticationToken {
        val claims = mapOf<String, Any>(
            "sub" to "google-sub-123",
            "email" to email,
            "email_verified" to emailVerified,
            "name" to name,
        )
        val idToken = OidcIdToken(
            "id-token-value",
            Instant.now(),
            Instant.now().plus(Duration.ofMinutes(5)),
            claims,
        )
        val authorities = listOf(SimpleGrantedAuthority("OIDC_USER"))
        return OAuth2AuthenticationToken(DefaultOidcUser(authorities, idToken), authorities, "google")
    }
}
