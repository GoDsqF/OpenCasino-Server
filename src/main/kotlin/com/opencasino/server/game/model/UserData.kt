package com.opencasino.server.game.model

import java.util.*

data class UserData(
    val id: UUID = UUID.randomUUID(),
    val username: String,
    val firstName: String,
    val lastName: String,
    val email: String? = null,
)