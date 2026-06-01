package com.opencasino.server.network.pack.blackjack.shared

import com.opencasino.server.network.pack.InitPack

data class GameSettingsPack(
    /** UUID комнаты — клиент получает его сразу при JOIN_SUCCESS, не дожидаясь GAME_ROOM_START. */
    val roomId: String,
    /** ms между UPDATE-тиками; см. DEFAULT_LOOP_RATE. */
    val loopRate: Long,
) : InitPack
