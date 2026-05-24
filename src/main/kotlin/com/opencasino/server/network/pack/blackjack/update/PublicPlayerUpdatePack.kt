package com.opencasino.server.network.pack.blackjack.update

import com.opencasino.server.network.pack.PublicUpdatePack
import com.opencasino.server.service.shared.BlackjackDecision

data class PublicPlayerUpdatePack(
    val id: Long,
    val lastDecision: BlackjackDecision,
    // Этот сидящий — получатель данного UPDATE. Сервер знает адресата безусловно
    // (сессия + Principal), поэтому FE не сравнивает pub.id с локальным meId.
    // Сейчас BJ single-player (MAX_BLACKJACK_PLAYERS=1) — всегда true; добавлено
    // заранее, чтобы multi-player BJ не плодил отдельный rebuy/identity-flow.
    val isYou: Boolean,
) : PublicUpdatePack
