package com.opencasino.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties


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

@ConfigurationProperties(prefix = "auth")
@EnableConfigurationProperties(Auth::class)
class Auth{

    private var tokenSecret: String = ""
    private var tokenExpirationMsec: String = ""

    fun getTokenSecret(): String {
        return tokenSecret
    }

    fun setTokenSecret(tokenSecret: String) {
        this.tokenSecret = tokenSecret
    }

    fun getTokenExpirationMsec(): String {
        return tokenExpirationMsec
    }

    fun setTokenExpirationMsec(tokenExpirationMsec: Long) {
        this.tokenExpirationMsec = tokenExpirationMsec.toString()
    }
}

@ConfigurationProperties(prefix = "oauth2")
@EnableConfigurationProperties(OAuth2Properties::class)
data class OAuth2Properties(
    var clientId: String = "",
    var clientSecret: String = "",
    var redirectUri: String = ""
)


@ConfigurationProperties(prefix = "application")
data class ApplicationProperties(
    val room: RoomProperties = RoomProperties(),
    val game: GameProperties = GameProperties(),
    val auth: Auth = Auth(),
    val oauth2: OAuth2Properties = OAuth2Properties(),
)