package com.opencasino.server.config

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

// Lets the app run on env-vars alone, without /config/*.properties mounted.
//
// Most short env-vars (APP_JWT_*, SERVER_SSL_*, DATABASE_*) already bind via Spring
// relaxed-binding because their canonical property names match after underscore/dot
// and case normalization. Two things do not fit and need explicit handling here:
//   1. OAuth client props: GOOGLE_OAUTH_CLIENT_ID has no naming relationship to
//      spring.security.oauth2.client.registration.google.client-id, so relaxed
//      binding cannot connect them. We add an explicit alias.
//   2. Defaults that live inside auth.properties / ssl.properties: those files are
//      gitignored and absent in CI-built JARs, so without /config/* mounted the
//      app starts with app.jwt.issuer etc. unset.
//
// Injected as addLast (lowest precedence): any /config/*.properties or env-var
// using the canonical Spring name still wins. Empty env-vars are ignored because
// Spring Boot's OAuth2 autoconfig refuses to build a registration with blank
// client-id.
class ShortEnvAliasPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val mapped = mutableMapOf<String, Any>()

        applyOAuthProvider(environment, mapped, registrationId = "google", envPrefix = "GOOGLE_OAUTH")

        environment.lookupNonBlank("OAUTH_SUCCESS_REDIRECT")?.let {
            mapped.putIfAbsent("app.auth.oauth2.success-redirect", it)
        }
        environment.lookupNonBlank("OAUTH_FAILURE_REDIRECT")?.let {
            mapped.putIfAbsent("app.auth.oauth2.failure-redirect", it)
        }

        for ((property, default) in CODE_DEFAULTS) {
            mapped.putIfAbsent(property, default)
        }

        if (mapped.isNotEmpty()) {
            environment.propertySources.addLast(MapPropertySource(PROPERTY_SOURCE_NAME, mapped))
        }
    }

    private fun applyOAuthProvider(
        env: ConfigurableEnvironment,
        out: MutableMap<String, Any>,
        registrationId: String,
        envPrefix: String,
    ) {
        val clientId = env.lookupNonBlank("${envPrefix}_CLIENT_ID") ?: return
        val clientSecret = env.lookupNonBlank("${envPrefix}_CLIENT_SECRET") ?: return
        val base = "spring.security.oauth2.client.registration.$registrationId"
        out["$base.client-id"] = clientId
        out["$base.client-secret"] = clientSecret
        out["$base.scope"] = env.lookupNonBlank("${envPrefix}_SCOPE") ?: defaultScopeFor(registrationId)
    }

    private fun defaultScopeFor(registrationId: String): String = when (registrationId) {
        "google" -> "openid,email,profile"
        else -> "openid,email,profile"
    }

    private fun ConfigurableEnvironment.lookupNonBlank(key: String): String? =
        getProperty(key)?.takeIf { it.isNotBlank() }

    companion object {
        const val PROPERTY_SOURCE_NAME = "opencasino-short-env-aliases"

        private val CODE_DEFAULTS: Map<String, String> = mapOf(
            "app.jwt.issuer" to "opencasino",
            "app.jwt.accessTtl" to "PT15M",
            "app.jwt.keyId" to "default",
            "app.jwt.privateKeyPath" to "/certs/jwt-private.pem",
            "app.jwt.publicKeyPath" to "/certs/jwt-public.pem",
            "app.auth.displayNameBlocklist" to "admin,root,system,support,moderator",
            "server.ssl.enabled" to "false",
        )
    }
}