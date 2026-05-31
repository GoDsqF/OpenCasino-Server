package com.opencasino.server.config

import com.opencasino.server.rng.RngProfile
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

data class GameProperties(
    val gameThreads: Int = 4,
)

data class HeartbeatProperties(
    val enabled: Boolean = true,
    val interval: Duration = Duration.ofSeconds(30),
    val pongTimeout: Duration = Duration.ofSeconds(10),
)

enum class AvailableGames {
    Blackjack,
    Poker,
    Crash,
}

data class BlackjackRoomProperties(
    val loopRate: Long = DEFAULT_LOOP_RATE,
    val initDelay: Long = ROOM_INIT_DELAY,
    val startDelay: Long = ROOM_START_DELAY,
    val endDelay: Long = ROOM_END_DELAY,
    val maxPlayers: Int = MAX_BLACKJACK_PLAYERS,
    val minBet: Double = MIN_BLACKJACK_BET,
    val deckStacks: Int = BLACKJACK_DECK_STACKS,
    val reshuffleThreshold: Int = BLACKJACK_RESHUFFLE_THRESHOLD,
    val actionTimeoutMs: Long = ACTION_TIMEOUT_MS,
)

data class PokerRoomProperties(
    val loopRate: Long = DEFAULT_LOOP_RATE,
    val initDelay: Long = ROOM_INIT_DELAY,
    val startDelay: Long = ROOM_START_DELAY,
    val endDelay: Long = ROOM_END_DELAY,
    val maxPlayers: Int = MAX_POKER_PLAYERS,
    val minPlayers: Int = MIN_POKER_PLAYERS,
    val minBet: Double = MIN_POKER_BET,
    val maxBet: Double = MAX_POKER_BET,
    val buyIn: Int = POKER_BUY_IN,
    val deckStacks: Int = POKER_DECK_STACKS,
    val showdownRevealMs: Long = SHOWDOWN_REVEAL_MS,
    val observerGraceMs: Long = OBSERVER_GRACE_MS,
    val actionTimeoutMs: Long = ACTION_TIMEOUT_MS,
)

data class CrashRoomProperties(
    val loopRate: Long = DEFAULT_LOOP_RATE,
    val initDelay: Long = ROOM_INIT_DELAY,
    val minBet: Double = CRASH_MIN_BET,
    val maxBet: Double = CRASH_MAX_BET,
    val bettingWindowMs: Long = CRASH_BETTING_WINDOW_MS,
    val cooldownMs: Long = CRASH_COOLDOWN_MS,
    val growthRate: Double = CRASH_GROWTH_RATE,
    val cashoutLagCompCapMs: Long = CRASH_CASHOUT_LAG_COMP_CAP_MS,
    val historySize: Int = CRASH_HISTORY_SIZE,
    val maxPlayers: Int = MAX_CRASH_PLAYERS,
)

data class RngProperties(
    val crash: RngProfile = RngProfile(houseEdge = CRASH_HOUSE_EDGE, maxPayout = CRASH_MAX_PAYOUT),
)

data class DatabaseProperties(
    var host: String,
    var port: Int,
    var database: String,
    var user: String,
    var password: String,
)

@ConfigurationProperties(prefix = "application")
data class ApplicationProperties(
    val blackjackRoom: BlackjackRoomProperties = BlackjackRoomProperties(),
    val pokerRoom: PokerRoomProperties = PokerRoomProperties(),
    val crashRoom: CrashRoomProperties = CrashRoomProperties(),
    val game: GameProperties = GameProperties(),
    val heartbeat: HeartbeatProperties = HeartbeatProperties(),
    val rng: RngProperties = RngProperties(),
)
