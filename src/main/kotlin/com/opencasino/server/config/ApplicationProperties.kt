package com.opencasino.server.config

import org.springframework.boot.context.properties.ConfigurationProperties


data class GameProperties(
    val gameThreads: Int = 4
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
    val maxPlayers: Int = MAX_POCKER_PLAYERS,
    val minPlayers: Int = MIN_POCKER_PLAYERS,
    val buyIn: Int = POKER_BUY_IN
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
)