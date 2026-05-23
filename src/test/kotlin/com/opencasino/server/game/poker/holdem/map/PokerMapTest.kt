package com.opencasino.server.game.poker.holdem.map

import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.game.poker.holdem.room.PokerGameRoom
import com.opencasino.server.network.shared.PlayerSession
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class PokerMapTest {
    private lateinit var map: PokerMap
    private val mockRoom = mock(PokerGameRoom::class.java)
    private val mockSession = mock(PlayerSession::class.java)

    @BeforeEach
    fun setUp() {
        map = PokerMap()
    }

    private fun createPlayer(id: Long = map.nextPlayerId()): PokerPlayer = PokerPlayer(id, mockRoom, mockSession)

    @Test
    fun `initially has no players`() {
        assertTrue(map.getPlayers().isEmpty())
    }

    @Test
    fun `addPlayer adds and assigns position`() {
        val p1 = createPlayer(1L)
        val p2 = createPlayer(2L)
        map.addPlayer(p1)
        map.addPlayer(p2)
        assertEquals(2, map.getPlayers().size)
        assertEquals(0, p1.position)
        assertEquals(1, p2.position)
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
        map.removePlayer(player)
        assertEquals(0, map.getPlayers().size)
    }

    @Test
    fun `does not exceed max players (6)`() {
        repeat(8) {
            map.addPlayer(createPlayer(it.toLong()))
        }
        assertEquals(6, map.getPlayers().size)
    }

    @Test
    fun `nextPlayerId generates unique ids`() {
        val ids = (1..1000).map { map.nextPlayerId() }.toSet()
        assertEquals(1000, ids.size)
    }

    @Test
    fun `alivePlayers counts correctly`() {
        val p1 = createPlayer(1L)
        val p2 = createPlayer(2L)
        map.addPlayer(p1)
        map.addPlayer(p2)

        p1.isAlive = true
        p2.isAlive = true
        assertEquals(2, map.alivePlayers())

        p2.isAlive = false
        assertEquals(1, map.alivePlayers())
    }

    @Test
    fun `default mode is holdem`() {
        assertTrue(map.getIsHoldem())
    }

    @Test
    fun `setOmaha switches mode`() {
        map.setOmaha()
        assertFalse(map.getIsHoldem())
    }

    @Test
    fun `setHoldem restores mode`() {
        map.setOmaha()
        map.setHoldem()
        assertTrue(map.getIsHoldem())
    }

    @Test
    fun `getPlayerByPosition returns correct player`() {
        val p1 = createPlayer(1L)
        val p2 = createPlayer(2L)
        map.addPlayer(p1)
        map.addPlayer(p2)
        assertEquals(p1, map.getPlayerByPosition(0))
        assertEquals(p2, map.getPlayerByPosition(1))
    }

    @Test
    fun `getPlayerByPosition returns null for empty position`() {
        assertNull(map.getPlayerByPosition(5))
    }

    @Test
    fun `addPlayer reuses freed seat instead of colliding after a leave`() {
        // Регрессия: position раньше присваивался как players.size, что после
        // выхода игрока давало коллизию — новый садился на уже занятую позицию
        // (на FE — поверх центрального «моего» места). Теперь берётся
        // наименьшая свободная позиция.
        val p0 = createPlayer(1L)
        val p1 = createPlayer(2L)
        map.addPlayer(p0)
        map.addPlayer(p1)
        assertEquals(0, p0.position)
        assertEquals(1, p1.position)

        // P0 (позиция 0) выходит → свободно место 0, P1 остаётся на позиции 1.
        map.removePlayer(p0)

        val p2 = createPlayer(3L)
        map.addPlayer(p2)
        assertEquals(0, p2.position, "новый игрок должен занять освободившееся место 0")
        assertEquals(1, p1.position, "оставшийся игрок не двигается")
        // Позиции уникальны — getPlayerByPosition больше не неоднозначен.
        assertEquals(p2, map.getPlayerByPosition(0))
        assertEquals(p1, map.getPlayerByPosition(1))
    }
}
