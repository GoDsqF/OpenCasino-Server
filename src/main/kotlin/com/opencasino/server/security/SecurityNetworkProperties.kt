package com.opencasino.server.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.security")
data class SecurityNetworkProperties(
    val trustedProxies: List<String> = emptyList(),
)