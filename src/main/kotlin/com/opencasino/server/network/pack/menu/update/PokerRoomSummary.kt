package com.opencasino.server.network.pack.menu.update

data class PokerRoomSummary(
    /** UUID комнаты — используется как `reconnectKey` в join-event-е. */
    val roomId: String,
    /** Имя enum-а `PokerBetType` — `"FixedLimit" | "PotLimit" | "NoLimit"`. */
    val betType: String,
    @Deprecated("Дубликат bigBlind (на сервере bigBlind == bet). Используйте bigBlind; поле будет удалено.")
    val bet: Double,
    val smallBlind: Double,
    val bigBlind: Double,
    val currentPlayers: Int,
    val maxPlayers: Int,
    /** Сколько игроков нужно, чтобы WAITING-комната стартовала раздачу. */
    val minPlayers: Int,
    /** Минимальный buy-in для этого стола (фактический порог, который проверяется в onBuyIn). */
    val minBuyIn: Double,
    /** Верхний потолок buy-in; null = без потолка, ограничен только балансом игрока. */
    val maxBuyIn: Double?,
    /** `"WAITING"` (ждёт игроков, раздача не стартовала) или `"IN_GAME"` (раздача идёт). */
    val phase: String, // ts: 'WAITING' | 'IN_GAME'
)
