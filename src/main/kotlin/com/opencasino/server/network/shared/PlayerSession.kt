package com.opencasino.server.network.shared

import com.opencasino.server.game.model.AbstractEntity
import org.springframework.web.reactive.socket.HandshakeInfo
import java.security.Principal
import java.util.*

open class PlayerSession<P>(
    override val id: String,
    open val handshakeInfo: HandshakeInfo
) : AbstractEntity<String>(id) {
    open var player: P? = null
    open var roomKey: UUID? = null
    open var principal: Principal? = null
    override fun toString(): String = "UserSession [id=" + id + "player=" + player + ", parentGameRoom=" + roomKey + "]"
}