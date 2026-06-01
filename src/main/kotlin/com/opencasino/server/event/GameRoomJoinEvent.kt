package com.opencasino.server.event

data class GameRoomJoinEvent(
    /** UUID существующей poker-комнаты при подключении вторым+ игроком.
     *  Для blackjack и при создании poker-комнаты — null. */
    val reconnectKey: String?,
    /** @deprecated Phase 5: сервер игнорирует. Identity берётся из JWT (`sub` claim =
     *  users.id) в handshake. Оставлено для обратной совместимости со старыми клиентами. */
    val playerUUID: String,
    /** Crash: выбор режима стола (SINGLE — комната-на-игрока, MULTI — общий стол с
     *  непрерывной каденцией). null/неизвестное = SINGLE. Прочие игры поле игнорируют. */
    val mode: String? = null, // ts: CrashMode | null
) : AbstractEvent()
