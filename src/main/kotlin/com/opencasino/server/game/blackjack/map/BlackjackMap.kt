package com.opencasino.server.game.blackjack.map

import com.opencasino.server.config.MAX_BLACKJACK_PLAYERS
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import kotlin.collections.HashMap


class BlackjackMap {

    private val players: MutableMap<Long, BlackjackPlayer> = HashMap()

    fun getPlayerById(id: Long): BlackjackPlayer? = players[id]
    fun getPlayers(): Collection<BlackjackPlayer> = players.values
    fun addPlayer(player: BlackjackPlayer) {
        if (players.size < MAX_BLACKJACK_PLAYERS)
            players[player.id] = player
    }

    fun removePlayer(player: BlackjackPlayer) = players.remove(player.id)

    fun nextPlayerId(): Long = (System.currentTimeMillis() shl 20) or (System.nanoTime() and 9223372036854251520L.inv())

    fun alivePlayers(): Long = players.values.stream().filter { it.isAlive }.count()
}