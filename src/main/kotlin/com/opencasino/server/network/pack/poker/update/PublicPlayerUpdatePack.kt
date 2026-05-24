package com.opencasino.server.network.pack.poker.update

import com.opencasino.server.network.pack.PublicUpdatePack
import com.opencasino.server.service.shared.PokerDecision

data class PublicPlayerUpdatePack(
    val id: Long,
    val position: Int,
    val displayName: String,
    val lastDecision: PokerDecision,
    val stack: Double,
    val currentBet: Double,
    val folded: Boolean,
    val allin: Boolean,
    // Игрок сидит за столом, но не профинансирован (busted-observer или ещё не
    // сделал buy-in): карт не получает, в раздаче не участвует, не блокирует
    // старт. Раньше FE угадывал это эвристикой `stack===0 && phase==='SHOWDOWN'`,
    // которая рассыпалась при любом сдвиге server-таймингов.
    val observer: Boolean,
) : PublicUpdatePack
