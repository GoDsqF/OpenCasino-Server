package com.opencasino.server.game.model

data class PlayersData(
    val username: String,
    val firstName: String,
    val lastName: String,
    val email: String? = null,
)