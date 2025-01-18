package com.opencasino.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

data class GameProperties(
    val gameThreads: Int = 4
)

data class RoomProperties(
    val loopRate: Long = DEFAULT_LOOP_RATE,
    val initDelay: Long = ROOM_INIT_DELAY,
    val startDelay: Long = ROOM_START_DELAY,
    val endDelay: Long = ROOM_END_DELAY,
    val maxPlayers: Int = MAX_PLAYERS
)

@ConfigurationProperties(prefix = "application")
data class ApplicationProperties(
    val room: RoomProperties = RoomProperties(),
    val game: GameProperties = GameProperties()
)