package com.opencasino.server.game.poker.holdem.model

import com.opencasino.server.config.MIN_BLACKJACK_BET
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.poker.holdem.room.PokerGameRoom
import com.opencasino.server.network.pack.poker.info.PlayerInfoPack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack
import com.opencasino.server.network.pack.poker.update.PrivatePlayerUpdatePack
import com.opencasino.server.network.shared.PlayerSession

import com.opencasino.server.service.shared.PokerDecision

class PokerPlayer(
    id: Long, gameRoom: PokerGameRoom, userSession: PlayerSession,
) : PokerBasePlayer<PokerGameRoom, PlayerInfoPack, PlayerHandUpdatePack, PrivatePlayerUpdatePack>(
    id, gameRoom, userSession
) {

    init {
        bet = MIN_BLACKJACK_BET
        position = 0
        lastDecision = PokerDecision.NONE
        stack = 0.00
        playerDeck = CardDeck()
    }

    fun updateState(event: PokerDecision, amount: Int?) {
        lastDecision = event
        lastBet = amount
        madeDecision = true
    }

    override fun update() {
        if (madeDecision) {
            when(lastDecision) {
                PokerDecision.CHECK -> {
                    madeDecision = false
                    gameRoom.onCheck(this.userSession)
                }
                PokerDecision.CALL -> {
                    madeDecision = false
                    if (lastBet != null) {
                        if (lastBet!! > gameRoom.roomProperties.smallBlind) {
                            gameRoom.onCall(this.userSession, lastBet!!)
                        }
                    }

                }
                PokerDecision.RAISE -> {
                    madeDecision = false
                    gameRoom.onRaise(this.userSession, lastBet!!)
                }
                PokerDecision.FOLD -> {
                    madeDecision = false
                    folded = true
                    gameRoom.onFold(this.userSession)
                }
                else -> {
                    println("Read fucking rules")
                }
            }
        }
    }

    override fun info(): PlayerInfoPack {
        return getInfoPack()
    }

    override fun getUpdatePack(): PlayerHandUpdatePack {
        return PlayerHandUpdatePack(id, this.position, this.stack, playerDeck.getCards())
    }

    override fun getInfoPack(): PlayerInfoPack {
        return PlayerInfoPack(id, balance)
    }

    override fun getPrivateUpdatePack(): PrivatePlayerUpdatePack {
        return PrivatePlayerUpdatePack(id, this.position, lastDecision)
    }

}