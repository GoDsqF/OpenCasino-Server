package com.opencasino.server.game.poker.holdem.model

import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.poker.holdem.room.PokerGameRoom
import com.opencasino.server.network.pack.poker.info.PlayerInfoPack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack
import com.opencasino.server.network.pack.poker.update.PrivatePlayerUpdatePack
import com.opencasino.server.network.pack.poker.update.PublicPlayerUpdatePack
import com.opencasino.server.network.shared.PlayerSession

import com.opencasino.server.service.shared.PokerDecision

class PokerPlayer(
    id: Long, gameRoom: PokerGameRoom, userSession: PlayerSession,
) : PokerBasePlayer<PokerGameRoom, PlayerInfoPack, PlayerHandUpdatePack, PrivatePlayerUpdatePack>(
    id, gameRoom, userSession
) {

    init {
        bet = gameRoom.bet
        boughtIn = false
        position = 0
        lastDecision = PokerDecision.NONE
        stack = 0.00
        currentBet = 0.00
        playerDeck = CardDeck()
    }

    fun updateState(event: PokerDecision, amount: Double?) {
        lastDecision = event
        lastBet = amount
        madeDecision = true
    }

    override fun update() {
        if (madeDecision) {
            when(lastDecision) {
                PokerDecision.CHECK -> {
                    madeDecision = false
                    gameRoom.nextMove(this.userSession)
                }
                PokerDecision.CALL -> {
                    madeDecision = false
                    //should be somewhat transactional
                    if (lastBet.isValidBet(lastDecision)) {
                        currentBet = currentBet!! + lastBet!!
                        gameRoom.nextMove(this.userSession)
                    }
                }
                PokerDecision.RAISE -> {
                    madeDecision = false
                    if (lastBet.isValidBet(lastDecision)) {
                        currentBet = currentBet!! + lastBet!!
                        gameRoom.nextMove(this.userSession)
                    }
                }
                PokerDecision.FOLD -> {
                    madeDecision = false
                    folded = true
                    gameRoom.nextMove(this.userSession)
                }
                PokerDecision.ALL_IN -> {
                    madeDecision = false
                    allin = true

                }
                else -> {
                    madeDecision = false
                    gameRoom.sendFailure(userSession, "Decision ${lastDecision.name} is not supported")
                }
            }
        }
    }

    fun commitBet() {
        gameRoom.pot += currentBet!!
        currentBet = 0.00
    }

    private fun Double?.isValidBet(betType: PokerDecision): Boolean =
        when (betType) {
            PokerDecision.CALL -> {
                (this != null) && (this > 0) && (currentBet!! + this == gameRoom.lastMaxBet)
            }
            PokerDecision.RAISE -> {
                (this != null) && (this > 0) && (currentBet!! + this + gameRoom.bigBlind >= gameRoom.lastMaxBet)
            }
            else -> false
        }

    override fun info(): PlayerInfoPack {
        return getInfoPack()
    }

    private fun handValue(): String {
        if (this.isAlive) this.update()
        return PokerHand.fromList(this.playerDeck.getCards()).getHighestRank()
    }

    override fun getUpdatePack(): PlayerHandUpdatePack {
        return PlayerHandUpdatePack(
            getPublicUpdatePack(),
            playerDeck.getCards())
    }

    fun getSecretUpdatePack(): PlayerHandUpdatePack {
        val deck = mutableListOf<Card?>()
        repeat(playerDeck.getCards().size) {
            deck += null
        }
        return PlayerHandUpdatePack(getPublicUpdatePack(), deck)
    }

    override fun getInfoPack(): PlayerInfoPack {
        return PlayerInfoPack(id, balance)
    }

    override fun getPrivateUpdatePack(): PrivatePlayerUpdatePack {
        return PrivatePlayerUpdatePack(id, this.position, lastDecision)
    }

    fun getPublicUpdatePack(): PublicPlayerUpdatePack {
        return PublicPlayerUpdatePack(id, this.position, lastDecision)
    }

}