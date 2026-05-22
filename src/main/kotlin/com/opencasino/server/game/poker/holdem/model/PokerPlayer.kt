package com.opencasino.server.game.poker.holdem.model

import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.poker.holdem.room.PokerGameRoom
import com.opencasino.server.network.pack.poker.info.PlayerInfoPack
import com.opencasino.server.network.pack.poker.update.PrivatePlayerUpdatePack
import com.opencasino.server.network.pack.poker.update.PublicPlayerUpdatePack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.shared.FailureCode
import com.opencasino.server.service.shared.PokerDecision

class PokerPlayer(
    id: Long,
    gameRoom: PokerGameRoom,
    userSession: PlayerSession,
) : PokerBasePlayer<PokerGameRoom, PlayerInfoPack, PlayerHandUpdatePack, PrivatePlayerUpdatePack>(
        id,
        gameRoom,
        userSession,
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

    fun updateState(
        event: PokerDecision,
        amount: Double?,
    ) {
        lastDecision = event
        lastBet = amount
        madeDecision = true
    }

    override fun update() {
        if (madeDecision) {
            when (lastDecision) {
                PokerDecision.CHECK -> {
                    madeDecision = false
                    gameRoom.nextMove(this.userSession)
                }
                PokerDecision.CALL -> {
                    madeDecision = false
                    // should be somewhat transactional
                    if (lastBet.isValidBet(lastDecision)) {
                        commitToStake(lastBet!!)
                        gameRoom.nextMove(this.userSession)
                    }
                }
                PokerDecision.RAISE -> {
                    madeDecision = false
                    if (lastBet.isValidBet(lastDecision)) {
                        commitToStake(lastBet!!)
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
                    if (stack > 0.0) {
                        commitToStake(stack)
                    }
                    allin = true
                    gameRoom.nextMove(this.userSession)
                }
                else -> {
                    madeDecision = false
                    gameRoom.sendFailure(
                        userSession,
                        FailureCode.INVALID_DECISION,
                        "Decision ${lastDecision.name} is not supported",
                    )
                }
            }
        }
    }

    private fun commitToStake(amount: Double) {
        val taken = if (amount > stack) stack else amount
        stack -= taken
        currentBet = (currentBet ?: 0.0) + taken
        totalContribution += taken
        val newBet = currentBet ?: 0.0
        val previousMax = gameRoom.lastMaxBet
        if (newBet > previousMax) {
            gameRoom.lastMaxBet = newBet
            // Полный рейз (≥ previousMax + bigBlind) переоткрывает торги — отмечаем
            // позицию рейзера как новый "first actor". Short all-in (под-рейз)
            // повышает lastMaxBet, но по стандартному правилу action для уже
            // отыгравших не переоткрывается, поэтому маркер не двигаем.
            if (newBet >= previousMax + gameRoom.bigBlind) {
                gameRoom.markRaiser(this.position)
            }
        }
        // Колл/рейз последними фишками == олл-ин. Без этого следующая улица
        // выбирает drained-стек как актора (firstAliveFrom пропускает только
        // folded/allin), а availableActions() возвращает empty → цикл встаёт.
        if (stack <= 0.0) allin = true
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
                // Стандартное правило мин. рейза: новая суммарная ставка после
                // действия должна быть не меньше lastMaxBet + bigBlind. Иначе
                // действие — это либо колл, либо невалидный «недорейз».
                (this != null) && (this > 0) && (currentBet!! + this >= gameRoom.lastMaxBet + gameRoom.bigBlind)
            }
            else -> false
        }

    override fun info(): PlayerInfoPack = getInfoPack()

    private fun handValue(): String {
        if (this.isAlive) this.update()
        return PokerHand.fromList(this.playerDeck.getCards()).getHighestRank()
    }

    override fun getUpdatePack(): PlayerHandUpdatePack =
        PlayerHandUpdatePack(
            getPublicUpdatePack(),
            playerDeck.getCards(),
        )

    fun getSecretUpdatePack(): PlayerHandUpdatePack {
        val deck = mutableListOf<Card?>()
        repeat(playerDeck.getCards().size) {
            deck += null
        }
        return PlayerHandUpdatePack(getPublicUpdatePack(), deck)
    }

    override fun getInfoPack(): PlayerInfoPack = PlayerInfoPack(id, balance)

    override fun getPrivateUpdatePack(): PrivatePlayerUpdatePack =
        PrivatePlayerUpdatePack(
            id,
            this.position,
            stack,
            currentBet ?: 0.0,
            lastDecision,
            availableActions(),
        )

    fun availableActions(): List<String> {
        if (folded || allin || stack <= 0.0) return emptyList()
        val actor = gameRoom.actorPosition() ?: return emptyList()
        if (this.position != actor) return emptyList()
        val toCall = gameRoom.lastMaxBet - (currentBet ?: 0.0)
        val actions = mutableListOf(PokerDecision.FOLD.name, PokerDecision.ALL_IN.name)
        if (toCall <= 0.0) {
            actions.add(PokerDecision.CHECK.name)
        } else if (stack >= toCall) {
            actions.add(PokerDecision.CALL.name)
        }
        if (stack > toCall + gameRoom.bigBlind) {
            actions.add(PokerDecision.RAISE.name)
        }
        return actions
    }

    fun getPublicUpdatePack(): PublicPlayerUpdatePack =
        PublicPlayerUpdatePack(
            id,
            this.position,
            displayName,
            lastDecision,
            stack,
            currentBet ?: 0.0,
            folded,
            allin,
        )
}
