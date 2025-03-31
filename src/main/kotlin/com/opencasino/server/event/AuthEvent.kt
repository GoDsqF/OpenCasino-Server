package com.opencasino.server.event

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken

data class AuthEvent(
    val userID: String,
    val email: String
) : AbstractEvent()