package com.opencasino.server.game.blackjack.map

import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.network.shared.PlayerSession
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class BlackjackMapTest {

    private lateinit var map: BlackjackMap
    private val mockRoom = mock(BlackjackGameRoom::class.java)
    private val mockSession = mock(PlayerSession::class.java)

    @BeforeEach
    fun setUp() {
        map = BlackjackMap()
    }

    private fun createPlayer(id: Long = map.nextPlayerId()): BlackjackPlayer {
        return BlackjackPlayer(id, mockRoom, mockSession)
    }

    @Test
    fun `initially has no players`() {
        assertTrue(map.getPlayers().isEmpty())
    }

    @Test
    fun `addPlayer adds player to map`() {
        val player = createPlayer(1L)
        map.addPlayer(player)
        assertEquals(1, map.getPlayers().size)
    }

    @Test
    fun `getPlayerById returns correct player`() {
        val player = createPlayer(42L)
        map.addPlayer(player)
        assertEquals(player, map.getPlayerById(42L))
    }

    @Test
    fun `getPlayerById returns null for missing id`() {
        assertNull(map.getPlayerById(999L))
    }

    @Test
    fun `removePlayer removes from map`() {
        val player = createPlayer(1L)
        map.addPlayer(player)
        assertEquals(1, map.getPlayers().size)

        map.removePlayer(player)
        assertEquals(0, map.getPlayers().size)
        assertNull(map.getPlayerById(1L))
    }

    @Test
    fun `does not exceed max players`() {
        // MAX_BLACKJACK_PLAYERS = 1
        val p1 = createPlayer(1L)
        val p2 = createPlayer(2L)
        map.addPlayer(p1)
        map.addPlayer(p2)
        assertEquals(1, map.getPlayers().size)
    }

    @Test
    fun `nextPlayerId returns unique ids`() {
        val ids = (1..1000).map { map.nextPlayerId() }.toSet()
        assertEquals(1000, ids.size)
    }

    @Test
    fun `alivePlayers counts only alive players`() {
        val player = createPlayer(1L)
        map.addPlayer(player)

        player.isAlive = true
        assertEquals(1, map.alivePlayers())

        player.isAlive = false
        assertEquals(0, map.alivePlayers())
    }
}