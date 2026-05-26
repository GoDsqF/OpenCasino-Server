package com.opencasino.server.game.blackjack.model

import com.opencasino.server.config.MIN_BLACKJACK_BET
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.network.pack.blackjack.info.PlayerInfoPack
import com.opencasino.server.network.pack.blackjack.update.BlackjackHandView
import com.opencasino.server.network.pack.blackjack.update.PrivatePlayerUpdatePack
import com.opencasino.server.network.pack.blackjack.update.PublicPlayerUpdatePack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.shared.BlackjackDecision
import com.opencasino.server.service.shared.FailureCode

open class BlackjackPlayer(
    id: Long,
    gameRoom: BlackjackGameRoom,
    userSession: PlayerSession,
) : BlackjackBasePlayer<BlackjackGameRoom, PlayerInfoPack, PlayerHandUpdatePack, PrivatePlayerUpdatePack>(
        id,
        gameRoom,
        userSession,
    ) {
    init {
        bet = MIN_BLACKJACK_BET
        position = 0
        lastDecision = BlackjackDecision.NONE
        hands.add(BlackjackHand())
        activeHandIndex = 0
    }

    fun updateState(event: BlackjackDecision) {
        lastDecision = event
        madeDecision = true
    }

    override fun update() {
        if (!madeDecision) return
        madeDecision = false
        val decision = lastDecision
        val hand = currentHand()
        when (decision) {
            BlackjackDecision.STAND -> {
                hand.resolved = true
                gameRoom.onHandResolved(this)
            }
            BlackjackDecision.HIT -> {
                gameRoom.deck.dealCard(hand.deck)
                gameRoom.onPlayerHit(this)
            }
            BlackjackDecision.DOUBLE -> {
                if (!canDouble()) {
                    gameRoom.sendFailure(
                        userSession,
                        FailureCode.INVALID_DECISION,
                        "DOUBLE is not available",
                    )
                    return
                }
                balance -= hand.bet
                hand.bet *= 2.0
                hand.doubled = true
                gameRoom.deck.dealCard(hand.deck)
                hand.resolved = true
                gameRoom.onHandResolved(this)
            }
            BlackjackDecision.SPLIT -> {
                if (!canSplit()) {
                    gameRoom.sendFailure(
                        userSession,
                        FailureCode.INVALID_DECISION,
                        "SPLIT is not available",
                    )
                    return
                }
                val secondCard = hand.deck.removeAt(1)
                val newHand = BlackjackHand(bet = hand.bet, fromSplit = true)
                hand.fromSplit = true
                newHand.deck.addCard(secondCard)
                balance -= newHand.bet
                hands.add(newHand)
                gameRoom.deck.dealCard(hand.deck)
                gameRoom.deck.dealCard(newHand.deck)
                gameRoom.onSplitCompleted(this)
            }
            BlackjackDecision.NONE -> {
                gameRoom.sendFailure(
                    userSession,
                    FailureCode.INVALID_DECISION,
                    "Decision NONE is not supported",
                )
            }
        }
    }

    private fun canDouble(): Boolean {
        val hand = currentHand()
        return !hand.resolved &&
            hand.deck.getCards().size == 2 &&
            !hand.doubled &&
            balance >= hand.bet
    }

    private fun canSplit(): Boolean {
        if (hands.size != 1) return false
        val hand = currentHand()
        val cards = hand.deck.getCards()
        return !hand.resolved &&
            cards.size == 2 &&
            cards[0].rank == cards[1].rank &&
            balance >= hand.bet
    }

    override fun info(): PlayerInfoPack = getInfoPack()

    // Контракт IUpdatePackProvider (no-arg). Реальная рассылка идёт через
    // getUpdatePack(isYou) из collectUpdate, где известен адресат; здесь
    // безопасный default для случаев без получателя.
    override fun getUpdatePack(): PlayerHandUpdatePack = getUpdatePack(isYou = false)

    fun getUpdatePack(isYou: Boolean): PlayerHandUpdatePack =
        PlayerHandUpdatePack(getPublicUpdatePack(isYou), currentHand().deck.getCards())

    override fun getInfoPack(): PlayerInfoPack = PlayerInfoPack(id, balance)

    override fun getPrivateUpdatePack(): PrivatePlayerUpdatePack {
        val handViews =
            hands.map { h ->
                BlackjackHandView(
                    cards = h.deck.getCards(),
                    bet = h.bet,
                    resolved = h.resolved,
                    doubled = h.doubled,
                    fromSplit = h.fromSplit,
                )
            }
        val totalBet = hands.sumOf { it.bet }
        return PrivatePlayerUpdatePack(
            id,
            balance,
            totalBet,
            lastDecision,
            availableActions(),
            handViews,
            activeHandIndex,
        )
    }

    // Игроку сейчас нужно сходить (есть доступные действия) — для турн-таймера и
    // clientState=AWAITING_TURN в комнате.
    fun canAct(): Boolean = availableActions().isNotEmpty()

    // Игрок уже прислал решение, оно ждёт обработки на следующем тике — окно хода
    // перевзводится, авто-stand по таймауту не нужен.
    fun hasPendingDecision(): Boolean = madeDecision

    private fun availableActions(): List<String> {
        if (!isAlive || madeDecision) return emptyList()
        val hand = currentHand()
        if (hand.resolved || hand.bet <= 0.0 || hand.deck.getCards().isEmpty()) return emptyList()
        val actions = mutableListOf(BlackjackDecision.HIT.name, BlackjackDecision.STAND.name)
        if (canDouble()) actions.add(BlackjackDecision.DOUBLE.name)
        if (canSplit()) actions.add(BlackjackDecision.SPLIT.name)
        return actions
    }

    fun getPublicUpdatePack(isYou: Boolean): PublicPlayerUpdatePack = PublicPlayerUpdatePack(id, lastDecision, isYou)

    fun resetForNewRound() {
        hands.clear()
        hands.add(BlackjackHand())
        activeHandIndex = 0
        bet = 0.0
        lastDecision = BlackjackDecision.NONE
        madeDecision = false
    }

    fun advanceToNextHand(): Boolean {
        val next = hands.indexOfFirst { !it.resolved }
        return if (next == -1) {
            false
        } else {
            activeHandIndex = next
            true
        }
    }
}
