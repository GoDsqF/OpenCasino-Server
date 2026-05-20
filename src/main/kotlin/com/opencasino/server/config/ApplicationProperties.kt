package com.opencasino.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration


data class GameProperties(
    val gameThreads: Int = 4
)

data class HeartbeatProperties(
    val enabled: Boolean = true,
    val interval: Duration = Duration.ofSeconds(30),
    val pongTimeout: Duration = Duration.ofSeconds(10),
)

enum class AvailableGames {
    Blackjack,
    Poker
}

data class BlackjackRoomProperties(
    val loopRate: Long = DEFAULT_LOOP_RATE,
    val initDelay: Long = ROOM_INIT_DELAY,
    val startDelay: Long = ROOM_START_DELAY,
    val endDelay: Long = ROOM_END_DELAY,
    val maxPlayers: Int = MAX_BLACKJACK_PLAYERS,
    val minBet: Double = MIN_BLACKJACK_BET,
    val deckStacks: Int = BLACKJACK_DECK_STACKS,
    val reshuffleThreshold: Int = BLACKJACK_RESHUFFLE_THRESHOLD
)

data class PokerRoomProperties(
    val loopRate: Long = DEFAULT_LOOP_RATE,
    val initDelay: Long = ROOM_INIT_DELAY,
    val startDelay: Long = ROOM_START_DELAY,
    val endDelay: Long = ROOM_END_DELAY,
    val maxPlayers: Int = MAX_POKER_PLAYERS,
    val minPlayers: Int = MIN_POKER_PLAYERS,
    val buyIn: Int = POKER_BUY_IN,
    val deckStacks: Int = POKER_DECK_STACKS
)

data class DatabaseProperties(
    var host: String,
    var port: Int,
    var database: String,
    var user: String,
    var password: String
)


@ConfigurationProperties(prefix = "application")
data class ApplicationProperties(
    val blackjackRoom: BlackjackRoomProperties = BlackjackRoomProperties(),
    val pokerRoom: PokerRoomProperties = PokerRoomProperties(),
    val game: GameProperties = GameProperties(),
    val heartbeat: HeartbeatProperties = HeartbeatProperties(),
)