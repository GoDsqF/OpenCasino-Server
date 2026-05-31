package com.opencasino.server.service.impl

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.poker.holdem.room.PokerGameRoom
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.network.pack.menu.update.PokerRoomSummary
import com.opencasino.server.service.PokerLobbyService
import com.opencasino.server.service.RoomService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MenuServiceImplTest {
    private val blackjackRoomService: RoomService = mock()
    private val pokerRoomService: RoomService = mock()
    private val crashRoomService: RoomService = mock()
    private val pokerLobbyService: PokerLobbyService = mock()
    private val applicationProperties = ApplicationProperties()
    private val service =
        MenuServiceImpl(
            blackjackRoomService,
            pokerRoomService,
            crashRoomService,
            pokerLobbyService,
            applicationProperties,
        )

    @Test
    fun `snapshot lists both games`() {
        whenever(blackjackRoomService.getRooms()).thenReturn(emptyList())
        whenever(pokerRoomService.getRooms()).thenReturn(emptyList())

        val snap = service.getMenuSnapshot()
        assertEquals(setOf("Blackjack", "Poker", "Crash"), snap.games.map { it.name }.toSet())
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
        assertTrue(snap.pokerRooms.isEmpty())
    }

    @Test
    fun `snapshot exposes joinable poker rooms from lobby service`() {
        whenever(blackjackRoomService.getRooms()).thenReturn(emptyList())
        whenever(pokerRoomService.getRooms()).thenReturn(emptyList())
        val summary =
            PokerRoomSummary(
                roomId = "abc",
                betType = "PotLimit",
                bet = 100.0,
                smallBlind = 50.0,
                bigBlind = 100.0,
                currentPlayers = 1,
                maxPlayers = 6,
                minPlayers = 2,
                minBuyIn = 2000.0,
                maxBuyIn = null,
                phase = "WAITING",
            )
        whenever(pokerLobbyService.listJoinableRooms()).thenReturn(listOf(summary))

        val snap = service.getMenuSnapshot()
        assertEquals(listOf(summary), snap.pokerRooms)
    }

    @Test
    fun `game metadata carries per-game default settings`() {
        whenever(blackjackRoomService.getRooms()).thenReturn(emptyList())
        whenever(pokerRoomService.getRooms()).thenReturn(emptyList())

        val byName = service.getMenuSnapshot().games.associateBy { it.name }
        val poker = byName["Poker"]!!
        assertEquals(applicationProperties.pokerRoom.minBet, poker.minBet)
        assertEquals(applicationProperties.pokerRoom.maxBet, poker.maxBet)
        assertEquals(applicationProperties.pokerRoom.buyIn.toDouble(), poker.buyIn)
        assertEquals(applicationProperties.pokerRoom.maxPlayers, poker.maxPlayers)
        assertEquals(applicationProperties.pokerRoom.minPlayers, poker.minPlayers)

        // Blackjack: single-player, ставка с баланса — нет maxBet/buyIn.
        val bj = byName["Blackjack"]!!
        assertEquals(applicationProperties.blackjackRoom.minBet, bj.minBet)
        assertNull(bj.maxBet)
        assertNull(bj.buyIn)
        assertEquals(1, bj.maxPlayers)
        assertEquals(1, bj.minPlayers)

        // Crash R3: single-режим, одно место, без buyIn.
        val crash = byName["Crash"]!!
        assertEquals(applicationProperties.crashRoom.minBet, crash.minBet)
        assertEquals(applicationProperties.crashRoom.maxBet, crash.maxBet)
        assertNull(crash.buyIn)
        assertEquals(1, crash.maxPlayers)
        assertEquals(1, crash.minPlayers)
    }
}
