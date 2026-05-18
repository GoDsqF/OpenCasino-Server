package com.opencasino.server.service

import com.opencasino.server.network.pack.menu.update.PokerRoomSummary

interface PokerLobbyService {
    fun listJoinableRooms(): List<PokerRoomSummary>
}