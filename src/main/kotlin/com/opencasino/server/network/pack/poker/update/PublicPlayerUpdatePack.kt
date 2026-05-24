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
    // Единый источник истины «игрок сбросил». Раньше FE делал
    // `folded || lastDecision==='FOLD'` — двойной источник; теперь lastDecision
    // несёт только последнее действие (бейдж CALL/RAISE/CHECK), а факт фолда —
    // только здесь. Флаг также true у observer/disconnected (сидит, но не в раздаче).
    val folded: Boolean,
    val allin: Boolean,
    // Игрок сидит за столом, но не профинансирован (busted-observer или ещё не
    // сделал buy-in): карт не получает, в раздаче не участвует, не блокирует
    // старт. Раньше FE угадывал это эвристикой `stack===0 && phase==='SHOWDOWN'`,
    // которая рассыпалась при любом сдвиге server-таймингов.
    val observer: Boolean,
    // Этот сидящий — получатель данного UPDATE. Сервер знает адресата безусловно
    // (сессия + Principal), поэтому FE не нужно сравнивать pub.id с meIdRef.
    val isYou: Boolean,
    // Сейчас ход этого игрока (position == actorPosition). Тривиальное сравнение,
    // но повторялось в каждом клиенте; при side-pot all-in ordering усложнится.
    val acting: Boolean,
) : PublicUpdatePack
