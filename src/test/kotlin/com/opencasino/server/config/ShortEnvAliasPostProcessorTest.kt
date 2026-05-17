package com.opencasino.server.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.core.env.MapPropertySource
import org.springframework.mock.env.MockEnvironment

class ShortEnvAliasPostProcessorTest {

    private val processor = ShortEnvAliasPostProcessor()
    private val app = SpringApplication()

    @Test
    fun `maps GOOGLE_OAUTH_CLIENT envs to spring registration props`() {
        val env = MockEnvironment().apply {
            setProperty("GOOGLE_OAUTH_CLIENT_ID", "id-123")
            setProperty("GOOGLE_OAUTH_CLIENT_SECRET", "shh")
        }

        processor.postProcessEnvironment(env, app)

        assertEquals("id-123", env.getProperty("spring.security.oauth2.client.registration.google.client-id"))
        assertEquals("shh", env.getProperty("spring.security.oauth2.client.registration.google.client-secret"))
        assertEquals("openid,email,profile", env.getProperty("spring.security.oauth2.client.registration.google.scope"))
    }

    @Test
    fun `honours custom GOOGLE_OAUTH_SCOPE`() {
        val env = MockEnvironment().apply {
            setProperty("GOOGLE_OAUTH_CLIENT_ID", "id")
            setProperty("GOOGLE_OAUTH_CLIENT_SECRET", "s")
            setProperty("GOOGLE_OAUTH_SCOPE", "openid,email")
        }

        processor.postProcessEnvironment(env, app)

        assertEquals("openid,email", env.getProperty("spring.security.oauth2.client.registration.google.scope"))
    }

    @Test
    fun `skips google registration when credentials missing`() {
        val env = MockEnvironment()

        processor.postProcessEnvironment(env, app)

        assertNull(env.getProperty("spring.security.oauth2.client.registration.google.client-id"))
        assertNull(env.getProperty("spring.security.oauth2.client.registration.google.client-secret"))
    }

    @Test
    fun `skips google registration when credentials blank`() {
        val env = MockEnvironment().apply {
            setProperty("GOOGLE_OAUTH_CLIENT_ID", "")
            setProperty("GOOGLE_OAUTH_CLIENT_SECRET", "   ")
        }

        processor.postProcessEnvironment(env, app)

        assertNull(env.getProperty("spring.security.oauth2.client.registration.google.client-id"))
    }

    @Test
    fun `maps OAUTH redirect aliases`() {
        val env = MockEnvironment().apply {
            setProperty("OAUTH_SUCCESS_REDIRECT", "https://example.test/ok")
            setProperty("OAUTH_FAILURE_REDIRECT", "https://example.test/err")
        }

        processor.postProcessEnvironment(env, app)

        assertEquals("https://example.test/ok", env.getProperty("app.auth.oauth2.success-redirect"))
        assertEquals("https://example.test/err", env.getProperty("app.auth.oauth2.failure-redirect"))
    }

    @Test
    fun `code defaults fill jwt props when nothing else is set`() {
        val env = MockEnvironment()

        processor.postProcessEnvironment(env, app)

        assertEquals("opencasino", env.getProperty("app.jwt.issuer"))
        assertEquals("PT15M", env.getProperty("app.jwt.accessTtl"))
        assertEquals("default", env.getProperty("app.jwt.keyId"))
        assertEquals("/certs/jwt-private.pem", env.getProperty("app.jwt.privateKeyPath"))
        assertEquals("/certs/jwt-public.pem", env.getProperty("app.jwt.publicKeyPath"))
        assertEquals("false", env.getProperty("server.ssl.enabled"))
    }

    @Test
    fun `explicit property in higher source overrides code default`() {
        val env = MockEnvironment()
        env.propertySources.addFirst(
            MapPropertySource("config-file", mapOf("app.jwt.issuer" to "casino-prod"))
        )

        processor.postProcessEnvironment(env, app)

        assertEquals("casino-prod", env.getProperty("app.jwt.issuer"))
    }
}