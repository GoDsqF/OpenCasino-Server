package com.opencasino.server.network.pack.poker.update

import com.opencasino.server.network.pack.PrivateUpdatePack
import com.opencasino.server.service.shared.PokerDecision

data class PrivatePlayerUpdatePack(
    val id: Long,
    val position: Int,
    val stack: Double,
    val currentBet: Double,
    val lastDecision: PokerDecision,
    val availableActions: List<String>,
    // «Купи стек или сядешь observer-ом / уйдёшь». true, пока игрок сидит, но не
    // профинансирован (== observer): busted после раздачи либо ещё не сделал
    // buy-in. На успешном BET становится false. FE сводит rebuy-логику к одной
    // строке вместо эвристики `stack===0 && phase==='SHOWDOWN'`.
    val needsRebuy: Boolean,
) : PrivateUpdatePack
