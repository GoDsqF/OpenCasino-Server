package com.opencasino.server.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val issuer: String = "opencasino",
    val accessTtl: Duration = Duration.ofMinutes(15),
    val keyId: String = "default",
    val privateKeyPem: String = "",
    val publicKeyPem: String = "",
)