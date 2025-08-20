package com.opencasino.server.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment


data class GameProperties(
    val gameThreads: Int = 4
)

enum class AvailableGames {
    Blackjack,
    PockerHoldEm,
    PockerOmaha
}

data class BlackjackRoomProperties(
    val loopRate: Long = DEFAULT_LOOP_RATE,
    val initDelay: Long = ROOM_INIT_DELAY,
    val startDelay: Long = ROOM_START_DELAY,
    val endDelay: Long = ROOM_END_DELAY,
    val maxPlayers: Int = MAX_BLACKJACK_PLAYERS
)

data class PokerRoomProperties(
    val loopRate: Long = DEFAULT_LOOP_RATE,
    val initDelay: Long = ROOM_INIT_DELAY,
    val startDelay: Long = ROOM_START_DELAY,
    val endDelay: Long = ROOM_END_DELAY,
    val maxPlayers: Int = MAX_POCKER_PLAYERS,
    val minPlayers: Int = MIN_POCKER_PLAYERS,
    var smallBlind: Int = SMALL_BLIND,
    var defaultBigBlind: Int = BIG_BLIND,
    val buyIn: Int = POKER_BUY_IN
)

@ConfigurationProperties(prefix = "auth")
@EnableConfigurationProperties(Auth::class)
class Auth{

    private var tokenSecret: String = ""
    private var tokenExpirationMsec: String = ""
}

@Autowired
var environment: Environment? = null


data class OAuth2Properties(
    var clientId: String,
    var clientSecret: String,
    var redirectUri: List<String>
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
    //val auth: Auth = Auth(),
    val oauth2: OAuth2Properties = OAuth2Config().oauth2Configuration(),
)