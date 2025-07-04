package com.opencasino.server.game.blackjack.model

import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.network.pack.blackjack.init.BlackjackPlayerInitPack
import com.opencasino.server.network.pack.update.PlayerUpdatePack
import com.opencasino.server.network.pack.update.BlackjackPrivatePlayerUpdatePack
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.shared.BlackjackDecision

class BlackjackPlayer(
    id: Long, gameRoom: BlackjackGameRoom, userSession: PlayerSession,
) : BlackjackBasePlayer<BlackjackGameRoom, BlackjackPlayerInitPack, PlayerUpdatePack, BlackjackPrivatePlayerUpdatePack>(
    id, gameRoom, userSession
) {

    init {
        position = 0
        decision = BlackjackDecision.NONE
        playerDeck = mutableListOf()
    }

    fun updateState(decision: BlackjackDecision, state: Boolean) {
        movingState[decision] = state
        isAlive = movingState.containsValue(true)
    }

    override fun update() {
        val trueValue = if (isAlive) movingState.filterValues { it }.keys else null
        if (trueValue != null) {
            decision = trueValue.first()
        }
    }

    override fun init(): BlackjackPlayerInitPack {
        return getInitPack()
    }

    override fun getUpdatePack(): PlayerUpdatePack {
        return PlayerUpdatePack(id, playerDeck)
    }

    override fun getInitPack(): BlackjackPlayerInitPack {
        return BlackjackPlayerInitPack(id, balance)
    }

    override fun getPrivateUpdatePack(): BlackjackPrivatePlayerUpdatePack {
        return BlackjackPrivatePlayerUpdatePack(id, decision)
    }

}