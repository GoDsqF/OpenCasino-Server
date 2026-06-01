package com.opencasino.server.network.pack.blackjack.shared

data class BlackjackConditionPack(
    /** Per-hand outcome. Длина = числу рук, с которыми игрок дошёл до showdown
     *  (1 без SPLIT, 2+ после SPLIT). Порядок совпадает с
     *  BlackjackPrivatePlayerUpdatePack.hands. */
    val handConditions: List<String>, // ts: BlackjackCondition[]
)
