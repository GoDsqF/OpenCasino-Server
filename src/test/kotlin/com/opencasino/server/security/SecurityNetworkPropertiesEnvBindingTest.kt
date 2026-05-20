package com.opencasino.server.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

class SecurityNetworkPropertiesEnvBindingTest {

    // Asserts that the canonical short env-var name maps to app.security.trusted-proxies
    // via Spring relaxed binding on the "systemEnvironment" property source (the same
    // source Spring uses for OS env vars). Without an entry in ShortEnvAliasPostProcessor,
    // this proves the env var passes through to ConfigurationProperties on its own.

    @Test
    fun `APP_SECURITY_TRUSTED_PROXIES env var binds to app security trusted-proxies via relaxed binding`() {
        val env = StandardEnvironment()
        env.propertySources.replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                mapOf("APP_SECURITY_TRUSTED_PROXIES" to "10.0.0.0/8,127.0.0.1") as Map<String, Any>,
            ),
        )

        val bound = Binder.get(env).bind("app.security", SecurityNetworkProperties::class.java).get()
        assertEquals(listOf("10.0.0.0/8", "127.0.0.1"), bound.trustedProxies)
    }

    @Test
    fun `empty APP_SECURITY_TRUSTED_PROXIES yields empty list`() {
        val env = StandardEnvironment()
        env.propertySources.replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                mapOf("APP_SECURITY_TRUSTED_PROXIES" to "") as Map<String, Any>,
            ),
        )

        val bound = Binder.get(env).bind("app.security", SecurityNetworkProperties::class.java)
            .orElse(SecurityNetworkProperties())
        assertEquals(emptyList<String>(), bound.trustedProxies)
    }

    @Test
    fun `canonical property name also binds`() {
        val env = StandardEnvironment()
        env.propertySources.addFirst(
            MapPropertySource("test", mapOf("app.security.trusted-proxies" to "192.168.0.0/16"))
        )
        val bound = Binder.get(env).bind("app.security", SecurityNetworkProperties::class.java).get()
        assertEquals(listOf("192.168.0.0/16"), bound.trustedProxies)
    }
}
