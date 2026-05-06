package com.opencasino.server.service.impl

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MenuServiceImplTest {

    private val service = MenuServiceImpl()

    @Test
    fun `getAvailableGames returns non-empty list`() {
        val games = service.getAvailableGames()
        assertTrue(games.isNotEmpty())
    }

    @Test
    fun `getAvailableGames contains Blackjack`() {
        val games = service.getAvailableGames()
        assertTrue(games.contains("Blackjack"))
    }

    @Test
    fun `getTotalActivePlayers returns zero initially`() {
        assertEquals(0, service.getTotalActivePlayers())
    }
}