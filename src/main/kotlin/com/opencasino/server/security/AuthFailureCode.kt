package com.opencasino.server.security

enum class AuthFailureCode {
    INVALID_EMAIL,
    WEAK_PASSWORD,
    MALFORMED_REQUEST,
    EMAIL_TAKEN,
    INVALID_CREDENTIALS,
}