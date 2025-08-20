package com.opencasino.server.game.poker.holdem.map

import com.opencasino.server.config.MAX_POCKER_PLAYERS
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import kotlin.collections.HashMap


class PokerMap {

    private val players: MutableMap<Long, PokerPlayer> = HashMap()
    private var isHoldem: Boolean = true

    fun getPlayerById(id: Long): PokerPlayer? = players[id]
    fun getPlayers(): Collection<PokerPlayer> = players.values
    fun getPlayerByPosition(pos: Int): PokerPlayer? = players.filter { it.value.position == pos }[0]
    fun addPlayer(player: PokerPlayer) {
        if (players.size < MAX_POCKER_PLAYERS) {
            player.position = players.size
            players[player.id] = player
        }
    }

    fun setHoldem() {
        isHoldem = true
    }
    fun setOmaha() {
        isHoldem = false
    }

    fun getIsHoldem(): Boolean {
        return isHoldem
    }

    fun removePlayer(player: PokerPlayer) = players.remove(player.id)

    fun nextPlayerId(): Long = (System.currentTimeMillis() shl 20) or (System.nanoTime() and 9223372036854251520L.inv())

    fun alivePlayers(): Long = players.values.stream().filter { it.isAlive }.count()
}