package com.opencasino.server.game.model

import com.opencasino.server.config.MIN_BLACKJACK_BET
import com.opencasino.server.game.Initializable
import com.opencasino.server.network.pack.InitPack
import com.opencasino.server.network.pack.UpdatePack
import com.opencasino.server.network.pack.IInitPackProvider
import com.opencasino.server.network.pack.update.IUpdatePackProvider

abstract class PlayerEntity<ID, GR, IP : InitPack, UP : UpdatePack>(
    id: ID,
    protected var gameRoom: GR
) : AbstractEntity<ID>(id), Initializable<IP>,
    IUpdatePackProvider<UP>, IInitPackProvider<IP> {
    var madeDecision: Boolean = false
    var isAlive: Boolean = true
    open var position: Int = 0
    var bet: Double = MIN_BLACKJACK_BET
}