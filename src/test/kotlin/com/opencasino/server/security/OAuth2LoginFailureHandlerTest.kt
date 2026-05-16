package com.opencasino.server.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class OAuth2LoginFailureHandlerTest {

    private val noopChain = WebFilterChain { Mono.empty() }

    @Test
    fun `redirects to failureRedirect with OAUTH_PROVIDER_ERROR`() {
        val handler = OAuth2LoginFailureHandler(
            AuthProperties(
                oauth2 = OAuth2RedirectProperties(
                    successRedirect = "https://app.example.com/oauth2/redirect",
                    failureRedirect = "https://app.example.com/oauth2/error",
                ),
            ),
        )
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/login/oauth2/code/google").build())

        StepVerifier.create(
            handler.onAuthenticationFailure(WebFilterExchange(exchange, noopChain), BadCredentialsException("nope"))
        ).verifyComplete()

        assertEquals(HttpStatus.FOUND, exchange.response.statusCode)
        val location = exchange.response.headers.location.toString()
        assertTrue(location.startsWith("https://app.example.com/oauth2/error"), "got $location")
        assertTrue(location.contains("error=OAUTH_PROVIDER_ERROR"), "got $location")
    }

    @Test
    fun `falls back to successRedirect when failureRedirect unset`() {
        val handler = OAuth2LoginFailureHandler(
            AuthProperties(oauth2 = OAuth2RedirectProperties(successRedirect = "https://app/redir", failureRedirect = null))
        )
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())

        StepVerifier.create(
            handler.onAuthenticationFailure(WebFilterExchange(exchange, noopChain), BadCredentialsException("nope"))
        ).verifyComplete()

        val location = exchange.response.headers.location.toString()
        assertTrue(location.startsWith("https://app/redir"), "got $location")
        assertTrue(location.contains("error=OAUTH_PROVIDER_ERROR"), "got $location")
    }

    @Test
    fun `returns 500 when no redirect is configured`() {
        val handler = OAuth2LoginFailureHandler(AuthProperties())
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())

        StepVerifier.create(
            handler.onAuthenticationFailure(WebFilterExchange(exchange, noopChain), BadCredentialsException("nope"))
        ).verifyComplete()

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.response.statusCode)
    }
}
