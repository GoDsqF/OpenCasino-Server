package com.opencasino.server.network.pack.blackjack.shared

import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.model.AbstractEntity
import com.opencasino.server.game.model.Player
import com.opencasino.server.game.model.PlayersTable
import com.opencasino.server.network.shared.UserSession
import org.springframework.web.reactive.socket.HandshakeInfo
import java.security.Principal
import java.util.*

class BlackjackUserSession(
    override val id: String,
    override val handshakeInfo: HandshakeInfo,
) : UserSession<BlackjackPlayer>(id, handshakeInfo) {
    override var player: BlackjackPlayer? = null
    override var roomKey: UUID? = null
    override var principal: Principal? = null
    override fun toString(): String =
        "UserSession [id=$id, player=$player, parentGameRoom=$roomKey]"
}