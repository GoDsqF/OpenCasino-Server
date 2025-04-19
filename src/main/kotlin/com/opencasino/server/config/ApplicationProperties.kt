package com.opencasino.server.config

import com.google.api.client.util.Value
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
}

@ConfigurationProperties(prefix = "oauth2")
@EnableConfigurationProperties(OAuth2Properties::class)
data class OAuth2Properties(
    var clientId: String = System.getenv("CLIENTID"),
    var clientSecret: String = System.getenv("CLIENTSECRET"),
    var redirectUri: List<String> = listOf("http://localhost:3000/oauth2/redirect", "https://opencasino.duckdns.org/oauth2/redirect")
)


@ConfigurationProperties(prefix = "application")
data class ApplicationProperties(
    val room: RoomProperties = RoomProperties(),
    val game: GameProperties = GameProperties(),
    //val auth: Auth = Auth(),
    val oauth2: OAuth2Properties = OAuth2Properties(),
)