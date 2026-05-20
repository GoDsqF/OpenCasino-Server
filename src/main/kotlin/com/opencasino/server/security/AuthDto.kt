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
    val refreshExpiresAt: Instant,
    val tokenType: String = "Bearer",
)

data class RefreshRequest(val refreshToken: String? = null)
data class LogoutRequest(val refreshToken: String? = null)

data class MeResponse(
    val userId: UUID,
    val email: String?,
    val displayName: String,
    val roles: List<String>,
    val balance: Double,
)

data class AuthFailureBody(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null,
)

// One row of refresh_tokens exposed to its owner via GET /auth/sessions.
// Phase 7 rotates the token on every /auth/refresh, so createdAt reflects
// the most recent rotation (i.e. last refresh) rather than the original
// login. expiresAt is the absolute refresh_ttl deadline for this rotation.
data class SessionView(
    val id: UUID,
    val createdAt: Instant,
    val expiresAt: Instant,
    val userAgent: String?,
    val ip: String?,
)
