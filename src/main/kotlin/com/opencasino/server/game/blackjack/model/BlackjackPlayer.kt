package com.opencasino.server.game.blackjack.model

import com.opencasino.server.config.MIN_BLACKJACK_BET
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.network.pack.blackjack.info.BlackjackPlayerInfoPack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack
import com.opencasino.server.network.pack.update.BlackjackPrivatePlayerUpdatePack
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.shared.BlackjackDecision

class BlackjackPlayer(
    id: Long, gameRoom: BlackjackGameRoom, userSession: PlayerSession,
) : BlackjackBasePlayer<BlackjackGameRoom, BlackjackPlayerInfoPack, PlayerHandUpdatePack, BlackjackPrivatePlayerUpdatePack>(
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
                BlackjackDecision.DOUBLE -> {

                }
                BlackjackDecision.SPLIT -> {

                }
                BlackjackDecision.NONE -> {

                }
            }
        }
    }

    override fun info(): BlackjackPlayerInfoPack {
        return getInfoPack()
    }

    override fun getUpdatePack(): PlayerHandUpdatePack {
        return PlayerHandUpdatePack(id, playerDeck.getCards())
    }

    override fun getInfoPack(): BlackjackPlayerInfoPack {
        return BlackjackPlayerInfoPack(id, balance)
    }

    override fun getPrivateUpdatePack(): BlackjackPrivatePlayerUpdatePack {
        return BlackjackPrivatePlayerUpdatePack(id, lastDecision)
    }

}