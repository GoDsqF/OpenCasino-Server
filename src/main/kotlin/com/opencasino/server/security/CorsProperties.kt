package com.opencasino.server.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
    val allowedOriginPatterns: List<String> = emptyList(),
    val allowedMethods: List<String> = listOf("GET", "POST", "OPTIONS"),
    val allowedHeaders: List<String> = listOf("Authorization", "Content-Type", "Sec-WebSocket-Protocol"),
    val exposedHeaders: List<String> = emptyList(),
    val allowCredentials: Boolean = true,
    val maxAge: Duration = Duration.ofHours(1),
)
