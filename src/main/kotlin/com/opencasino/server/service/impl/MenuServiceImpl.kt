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
        val games = AvailableGames.entries.map { game ->
            val service = when (game) {
                AvailableGames.Blackjack -> blackjackRoomService
                AvailableGames.Poker -> pokerRoomService
            }
            val rooms = service.getRooms()
            GameMetadata(
                name = game.name,
                activeRooms = rooms.size,
                activePlayers = rooms.sumOf { it.currentPlayersCount() }
            )
        }
        return MenuUpdatePack(
            games = games,
            totalActivePlayers = games.sumOf { it.activePlayers },
            pokerRooms = pokerLobbyService.listJoinableRooms(),
        )
    }
}