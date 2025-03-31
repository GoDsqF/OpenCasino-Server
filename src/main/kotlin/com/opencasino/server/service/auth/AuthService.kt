package com.opencasino.server.service.auth

import com.opencasino.server.event.AuthEvent

interface AuthService {
    fun authenticate(authString: String): AuthEvent
}