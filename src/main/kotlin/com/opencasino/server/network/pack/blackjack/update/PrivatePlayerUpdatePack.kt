package com.opencasino.server.network.pack.blackjack.update

import com.opencasino.server.network.pack.PrivateUpdatePack
import com.opencasino.server.service.shared.BlackjackDecision

data class PrivatePlayerUpdatePack(
    val id: Long,
    val balance: Double,
    /** Сумма ставок по всем активным рукам игрока (после SPLIT — за обе). */
    val currentBet: Double,
    val lastDecision: BlackjackDecision,
    /** Имена разрешённых сейчас action-ов на активной руке. Пусто, если ход не на
     *  этом игроке или раунд завершён. Может включать DOUBLE (на первых двух картах
     *  при достаточном балансе) и SPLIT (на паре одинакового ранга, пока рука одна). */
    val availableActions: List<String>, // ts: BlackjackDecisionName[]
    // Все руки игрока в текущем раунде. До SPLIT — длина 1.
    val hands: List<BlackjackHandView>,
    /** Индекс активной руки в `hands` (на какую руку отыгрываются action-ы). */
    val activeHandIndex: Int,
) : PrivateUpdatePack
