package com.opencasino.server.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth")
data class AuthProperties(
    val displayNameBlocklist: List<String> = emptyList(),
)
