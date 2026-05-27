package com.opencasino.server.network.pack.poker.update

import com.opencasino.server.network.pack.UpdatePack
import com.opencasino.server.network.pack.shared.DealerUpdatePack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack
import com.opencasino.server.service.shared.ClientState
import com.opencasino.server.service.shared.PokerPhase

data class GameUpdatePack(
    val player: PrivatePlayerUpdatePack,
    val players: Collection<PlayerHandUpdatePack>,
    val dealer: DealerUpdatePack,
    val phase: PokerPhase,
    val currentPosition: Int?,
    val pot: Double,
    val lastMaxBet: Double,
    // Big blind стола — клиенту нужен для корректного минимального рейза
    // (min raise-to = lastMaxBet + bigBlind). Раньше FE хардкодил это значение
    // и при кастомном bet-е допускал под-рейз, который сервер молча отклонял
    // → ход застревал, выглядело как «комната сломалась».
    val bigBlind: Double,
    // Authoritative дедлайн хода текущего актора (epoch ms): когда он истечёт,
    // сервер сам сходит за игрока (fold/check) и сдвинет раздачу. Один на стол —
    // описывает текущего актора, поэтому все клиенты крутят одинаковый countdown.
    // null — действовать сейчас некому (showdown/пауза/нет актора).
    val turnDeadlineEpochMs: Long?,
    // High-level lifecycle-фаза ЭТОГО получателя (per-recipient): сервер знает
    // адресата и его состояние, поэтому отдаёт готовый enum вместо того, чтобы
    // каждый клиент собирал Phase-машину из socket.status + boughtIn +
    // availableActions. См. ClientState.
    val clientState: ClientState,
) : UpdatePack
