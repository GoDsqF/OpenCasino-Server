package com.opencasino.server.network.pack.poker.update

import com.opencasino.server.network.pack.PrivateUpdatePack
import com.opencasino.server.service.shared.PokerDecision

data class PrivatePlayerUpdatePack(
    val id: Long,
    val position: Int,
    val stack: Double,
    val currentBet: Double,
    val lastDecision: PokerDecision,
    val availableActions: List<String>, // ts: PokerDecisionName[]
    // «Купи стек или сядешь observer-ом / уйдёшь». true, пока игрок сидит, но не
    // профинансирован (== observer): busted после раздачи либо ещё не сделал
    // buy-in. На успешном BET становится false. FE сводит rebuy-логику к одной
    // строке вместо эвристики `stack===0 && phase==='SHOWDOWN'`.
    val needsRebuy: Boolean,
    // Derived betting-bounds — раньше FE считал их сам из lastMaxBet/currentBet/
    // stack плюс хардкоженный bigBlind (Defaults.poker.minBet). При любом сдвиге
    // правил (мульти-рейз cap, mixed-stake, PLO min-raise) клиентская эвристика
    // молча врала. Источник истины — те же выражения, что валидируют BET в
    // PokerPlayer.isValidBet, поэтому slider клиента не может предложить сумму,
    // которую сервер отклонит.
    //
    // Семантика (совпадает с FE до этой правки):
    //  callAmount — DELTA к коллу: сколько ДОБАВИТЬ к currentBet, чтобы сравняться
    //               с lastMaxBet (== amount в BET{CALL}). 0, если коллировать нечего.
    //  minRaise   — минимальный raise-TO (итоговый currentBet после рейза), == lastMaxBet + bigBlind.
    //  maxRaise   — максимальный raise-TO (== all-in): stack + currentBet.
    val callAmount: Double,
    val minRaise: Double,
    val maxRaise: Double,
) : PrivateUpdatePack
