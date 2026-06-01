package com.opencasino.server.service.impl

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.AvailableGames
import com.opencasino.server.network.pack.menu.update.GameMetadata
import com.opencasino.server.network.pack.menu.update.MenuUpdatePack
import com.opencasino.server.service.MenuService
import com.opencasino.server.service.PokerLobbyService
import com.opencasino.server.service.RoomService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service

@Service
class MenuServiceImpl(
    @Qualifier("blackjackRoomServiceImpl") @Lazy private val blackjackRoomService: RoomService,
    @Qualifier("pokerRoomServiceImpl") @Lazy private val pokerRoomService: RoomService,
    @Qualifier("crashRoomServiceImpl") @Lazy private val crashRoomService: RoomService,
    @Lazy private val pokerLobbyService: PokerLobbyService,
    private val applicationProperties: ApplicationProperties,
) : MenuService {
    override fun getMenuSnapshot(): MenuUpdatePack {
        val pokerRooms = pokerLobbyService.listJoinableRooms()
        val pokerWaitingPlayers = pokerRooms.sumOf { it.currentPlayers }
        val games =
            AvailableGames.entries.map { game ->
                val rooms = serviceFor(game).getRooms()
                // У Poker до launchRoom() игроки сидят в sessionQueue, поэтому
                // currentPlayersCount() возвращает 0 для свежесозданной комнаты.
                // Берём счётчик из listJoinableRooms() (учитывает очередь) для
                // joinable-комнат + currentPlayersCount() для уже запущенных,
                // чтобы создатель отображался в счётчике сразу.
                val activePlayers =
                    when (game) {
                        AvailableGames.Poker -> {
                            val running =
                                rooms.filter {
                                    pokerRooms.none { jr -> jr.roomId == it.key().toString() }
                                }
                            pokerWaitingPlayers + running.sumOf { it.currentPlayersCount() }
                        }
                        else -> rooms.sumOf { it.currentPlayersCount() }
                    }
                gameMetadata(game, rooms.size, activePlayers)
            }
        return MenuUpdatePack(
            games = games,
            totalActivePlayers = games.sumOf { it.activePlayers },
            pokerRooms = pokerRooms,
        )
    }

    private fun serviceFor(game: AvailableGames): RoomService =
        when (game) {
            AvailableGames.Blackjack -> blackjackRoomService
            AvailableGames.Poker -> pokerRoomService
            AvailableGames.Crash -> crashRoomService
        }

    // Дефолтные лимиты игры — единственный источник для клиентских Defaults.
    // Blackjack: single-player, ставка с баланса — нет maxBet/buyIn.
    private fun gameMetadata(
        game: AvailableGames,
        activeRooms: Int,
        activePlayers: Int,
    ): GameMetadata =
        when (game) {
            AvailableGames.Blackjack ->
                applicationProperties.blackjackRoom.let { bj ->
                    GameMetadata(
                        name = game.name,
                        activeRooms = activeRooms,
                        activePlayers = activePlayers,
                        minBet = bj.minBet,
                        maxBet = null,
                        buyIn = null,
                        maxPlayers = bj.maxPlayers,
                        minPlayers = 1,
                    )
                }
            AvailableGames.Poker ->
                applicationProperties.pokerRoom.let { poker ->
                    GameMetadata(
                        name = game.name,
                        activeRooms = activeRooms,
                        activePlayers = activePlayers,
                        minBet = poker.minBet,
                        maxBet = poker.maxBet,
                        buyIn = poker.buyIn.toDouble(),
                        maxPlayers = poker.maxPlayers,
                        minPlayers = poker.minPlayers,
                    )
                }
            // Crash: single (комната-на-игрока) и multi (общий стол на N) сосуществуют.
            // maxPlayers рекламируем по multi-вместимости (single-лимит = 1 — частный
            // случай); FE выбирает режим полем GameRoomJoinEvent.mode.
            AvailableGames.Crash ->
                applicationProperties.crashRoom.let { crash ->
                    GameMetadata(
                        name = game.name,
                        activeRooms = activeRooms,
                        activePlayers = activePlayers,
                        minBet = crash.minBet,
                        maxBet = crash.maxBet,
                        buyIn = null,
                        maxPlayers = crash.maxPlayers,
                        minPlayers = 1,
                    )
                }
        }
}
