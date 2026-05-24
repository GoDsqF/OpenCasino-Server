package com.opencasino.server.network.pack.menu.update

data class PokerRoomSummary(
    val roomId: String,
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
    val phase: String,
)
