package com.opencasino.server.service.impl

import com.opencasino.server.service.MenuService
import org.springframework.stereotype.Service

@Service
class MenuServiceImpl : MenuService {

    private val games = listOf("Blackjack")

    override fun getAvailableGames(): List<String> {
        return games
    }

    override fun getTotalActivePlayers(): Int {
        return 0
    }
}