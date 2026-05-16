package com.opencasino.server.service.impl

import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.poker.holdem.room.PokerGameRoom
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.service.RoomService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MenuServiceImplTest {

    private val blackjackRoomService: RoomService = mock()
    private val pokerRoomService: RoomService = mock()
    private val service = MenuServiceImpl(blackjackRoomService, pokerRoomService)

    @Test
    fun `snapshot lists both games`() {
        whenever(blackjackRoomService.getRooms()).thenReturn(emptyList())
        whenever(pokerRoomService.getRooms()).thenReturn(emptyList())

        val snap = service.getMenuSnapshot()
        assertEquals(setOf("Blackjack", "Poker"), snap.games.map { it.name }.toSet())
    }

    @Test
    fun `snapshot aggregates active players across games`() {
        val bjRoom: BlackjackGameRoom = mock()
        whenever(bjRoom.currentPlayersCount()).thenReturn(1)
        val pokerRoom: PokerGameRoom = mock()
        whenever(pokerRoom.currentPlayersCount()).thenReturn(3)

        whenever(blackjackRoomService.getRooms()).thenReturn(listOf<GameRoom>(bjRoom))
        whenever(pokerRoomService.getRooms()).thenReturn(listOf<GameRoom>(pokerRoom))

        val snap = service.getMenuSnapshot()
        assertEquals(4, snap.totalActivePlayers)
        val byName = snap.games.associateBy { it.name }
        assertEquals(1, byName["Blackjack"]!!.activeRooms)
        assertEquals(1, byName["Blackjack"]!!.activePlayers)
        assertEquals(1, byName["Poker"]!!.activeRooms)
        assertEquals(3, byName["Poker"]!!.activePlayers)
    }

    @Test
    fun `snapshot returns zero counts when no rooms`() {
        whenever(blackjackRoomService.getRooms()).thenReturn(emptyList())
        whenever(pokerRoomService.getRooms()).thenReturn(emptyList())

        val snap = service.getMenuSnapshot()
        assertEquals(0, snap.totalActivePlayers)
        assertTrue(snap.games.all { it.activeRooms == 0 && it.activePlayers == 0 })
    }
}