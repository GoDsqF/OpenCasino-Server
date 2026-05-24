package com.opencasino.server.network.pack.menu.update

import com.opencasino.server.network.pack.Pack

data class GameMetadata(
    val name: String,
    val activeRooms: Int,
    val activePlayers: Int,
    /** Дефолтные настройки игры — единственный источник для клиентских лимитов
     *  (FE раньше держал их в hardcoded Defaults-таблице). Nullable-поля = null
     *  для игр, у которых концепции нет (Blackjack не имеет maxBet/buyIn). */
    val minBet: Double? = null,
    val maxBet: Double? = null,
    val buyIn: Double? = null,
    val maxPlayers: Int,
    val minPlayers: Int,
)

data class MenuUpdatePack(
    val games: List<GameMetadata>,
    val totalActivePlayers: Int,
    val pokerRooms: List<PokerRoomSummary> = emptyList(),
) : Pack
