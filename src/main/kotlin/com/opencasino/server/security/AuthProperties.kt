package com.opencasino.server.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth")
data class AuthProperties(
    val displayNameBlocklist: List<String> = emptyList(),
    val oauth2: OAuth2RedirectProperties = OAuth2RedirectProperties(),
)

data class OAuth2RedirectProperties(
    val successRedirect: String = "",
    val failureRedirect: String? = null,
)