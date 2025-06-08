package com.opencasino.server.network.shared

import com.opencasino.server.game.model.AbstractEntity
import com.opencasino.server.game.model.Entity
import com.opencasino.server.game.model.Player
import org.springframework.web.reactive.socket.HandshakeInfo
import java.security.Principal
import java.util.*

open class PlayerSession(
    override val id: String,
    open val handshakeInfo: HandshakeInfo
) : AbstractEntity<String>(id) {
    open var player: Entity<Long>? = null
    open var roomKey: UUID? = null
    open var principal: Principal? = null
    override fun toString(): String = "UserSession [id=" + id + "player=" + player + ", parentGameRoom=" + roomKey + "]"
}