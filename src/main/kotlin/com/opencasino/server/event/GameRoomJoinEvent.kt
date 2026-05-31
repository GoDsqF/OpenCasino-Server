package com.opencasino.server.event

data class GameRoomJoinEvent(
    val reconnectKey: String?,
    val playerUUID: String,
    // Crash: выбор режима стола (SINGLE — комната-на-игрока, MULTI — общий стол с
    // непрерывной каденцией). null/неизвестное = SINGLE. Прочие игры поле игнорируют.
    val mode: String? = null,
) : AbstractEvent()
