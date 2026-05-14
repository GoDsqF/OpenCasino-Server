package com.opencasino.server.game.blackjack.model

import com.opencasino.server.config.MIN_BLACKJACK_BET
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.network.pack.blackjack.info.PlayerInfoPack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack
import com.opencasino.server.network.pack.blackjack.update.PrivatePlayerUpdatePack
import com.opencasino.server.network.pack.blackjack.update.PublicPlayerUpdatePack
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.shared.BlackjackDecision
import com.opencasino.server.service.shared.FailureCode

open class BlackjackPlayer(
    id: Long, gameRoom: BlackjackGameRoom, userSession: PlayerSession,
) : BlackjackBasePlayer<BlackjackGameRoom, PlayerInfoPack, PlayerHandUpdatePack, PrivatePlayerUpdatePack>(
    id, gameRoom, userSession
) {

    init {
        bet = MIN_BLACKJACK_BET
        position = 0
        lastDecision = BlackjackDecision.NONE
        playerDeck = CardDeck()
    }

    fun updateState(event: BlackjackDecision) {
        lastDecision = event
        madeDecision = true
    }

    override fun update() {
        if (madeDecision) {
            when(lastDecision) {
                BlackjackDecision.STAND -> {
                    madeDecision = false
                    gameRoom.onDealerTurn()
                }
                BlackjackDecision.HIT -> {
                    gameRoom.deck.dealCard(playerDeck)
                    madeDecision = false
                    gameRoom.onPlayerTurn()
                }
                BlackjackDecision.DOUBLE,
                BlackjackDecision.SPLIT,
                BlackjackDecision.NONE -> {
                    madeDecision = false
                    gameRoom.sendFailure(
                        userSession,
                        FailureCode.INVALID_DECISION,
                        "Decision ${lastDecision.name} is not supported"
                    )
                }
            }
        }
    }

    override fun info(): PlayerInfoPack {
        return getInfoPack()
    }

    override fun getUpdatePack(): PlayerHandUpdatePack {
        return PlayerHandUpdatePack(getPublicUpdatePack(), playerDeck.getCards())
    }

    override fun getInfoPack(): PlayerInfoPack {
        return PlayerInfoPack(id, balance)
    }

    override fun getPrivateUpdatePack(): PrivatePlayerUpdatePack {
        return PrivatePlayerUpdatePack(id, balance, bet, lastDecision, availableActions())
    }

    private fun availableActions(): List<String> =
        if (isAlive && !madeDecision && bet > 0.0 && playerDeck.getCards().isNotEmpty())
            listOf(BlackjackDecision.HIT.name, BlackjackDecision.STAND.name)
        else emptyList()

    fun getPublicUpdatePack(): PublicPlayerUpdatePack {
        return PublicPlayerUpdatePack(id, lastDecision)
    }

}