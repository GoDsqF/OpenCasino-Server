package com.opencasino.server.game.blackjack.model

import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.network.pack.blackjack.init.BlackjackPlayerInitPack
import com.opencasino.server.network.pack.blackjack.shared.BlackjackUserSession
import com.opencasino.server.network.pack.blackjack.update.PlayerUpdatePack
import com.opencasino.server.network.pack.blackjack.update.BlackjackPrivatePlayerUpdatePack
import com.opencasino.server.service.blackjack.shared.BlackjackDecision
import java.util.UUID

class BlackjackPlayer(
    id: UUID, gameRoom: BlackjackGameRoom, userSession: BlackjackUserSession,
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