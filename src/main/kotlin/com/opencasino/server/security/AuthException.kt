package com.opencasino.server.security

class AuthException(
    val failure: AuthFailureCode,
    val detail: String? = null,
) : RuntimeException("auth failure: $failure")