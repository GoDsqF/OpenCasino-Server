package com.opencasino.server.network.pack.blackjack.update

import com.opencasino.server.network.pack.UpdatePack
import com.opencasino.server.network.pack.shared.DealerUpdatePack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack
import com.opencasino.server.service.shared.ClientState

data class GameUpdatePack(
    val player: PrivatePlayerUpdatePack,
    val players: Collection<PlayerHandUpdatePack>,
    val dealer: DealerUpdatePack,
    // Authoritative дедлайн хода (epoch ms): по истечении сервер сам делает STAND
    // текущей руки и доигрывает раздачу. null — ходить сейчас некому. См. поле-
    // близнец в poker GameUpdatePack.
    val turnDeadlineEpochMs: Long?,
    // High-level lifecycle-фаза получателя — единый источник истины вместо
    // локальной Phase-машины на BlackjackPage. См. ClientState.
    val clientState: ClientState,
) : UpdatePack
