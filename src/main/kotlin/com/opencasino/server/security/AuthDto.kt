package com.opencasino.server.security

import java.time.Instant
import java.util.UUID

data class RegisterRequest(
    val email: String? = null,
    val password: String? = null,
    val displayName: String? = null,
)
data class LoginRequest(val email: String? = null, val password: String? = null)

data class RegisterResponse(val userId: UUID, val email: String, val displayName: String)

data class LoginResponse(
    val userId: UUID,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val tokenType: String = "Bearer",
)

data class MeResponse(
    val userId: UUID,
    val email: String?,
    val roles: List<String>,
)

data class AuthFailureBody(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null,
)
