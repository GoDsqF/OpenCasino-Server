package com.opencasino.server.service.impl

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
    @Lazy private val pokerLobbyService: PokerLobbyService,
) : MenuService {
    override fun getMenuSnapshot(): MenuUpdatePack {
        val pokerRooms = pokerLobbyService.listJoinableRooms()
        val pokerWaitingPlayers = pokerRooms.sumOf { it.currentPlayers }
        val games =
            AvailableGames.entries.map { game ->
                val service =
                    when (game) {
                        AvailableGames.Blackjack -> blackjackRoomService
                        AvailableGames.Poker -> pokerRoomService
                    }
                val rooms = service.getRooms()
                // У Poker до launchRoom() игроки сидят в sessionQueue, поэтому
                // currentPlayersCount() возвращает 0 для свежесозданной комнаты.
                // Берём счётчик из listJoinableRooms() (учитывает очередь) для
                // joinable-комнат + currentPlayersCount() для уже запущенных,
                // чтобы создатель отображался в счётчике сразу.
                val activePlayers =
                    when (game) {
                        AvailableGames.Poker -> {
                            val running = rooms.filter {
                                pokerRooms.none { jr -> jr.roomId == it.key().toString() }
                            }
                            pokerWaitingPlayers + running.sumOf { it.currentPlayersCount() }
                        }
                        else -> rooms.sumOf { it.currentPlayersCount() }
                    }
                GameMetadata(
                    name = game.name,
                    activeRooms = rooms.size,
                    activePlayers = activePlayers,
                )
            }
        return MenuUpdatePack(
            games = games,
            totalActivePlayers = games.sumOf { it.activePlayers },
            pokerRooms = pokerRooms,
        )
    }
}
