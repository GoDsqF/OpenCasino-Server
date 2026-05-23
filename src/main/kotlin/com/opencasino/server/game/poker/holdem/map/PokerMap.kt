package com.opencasino.server.game.poker.holdem.map

import com.opencasino.server.config.MAX_POKER_PLAYERS
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import java.util.concurrent.atomic.AtomicLong

class PokerMap {
    companion object {
        private val idCounter = AtomicLong(System.currentTimeMillis())
    }

    private val players: MutableMap<Long, PokerPlayer> = HashMap()
    private var isHoldem: Boolean = true

    fun getPlayerById(id: Long): PokerPlayer? = players[id]

    fun getPlayers(): Collection<PokerPlayer> = players.values

    fun getPlayerByPosition(pos: Int): PokerPlayer? = players.values.firstOrNull { it.position == pos }

    fun addPlayer(player: PokerPlayer) {
        if (players.size < MAX_POKER_PLAYERS) {
            // Берём наименьшую свободную позицию, а не players.size. Размер
            // не отражает занятые слоты после выхода игрока: например, при
            // {pos0, pos1} → выходит pos0 → size=1 → новому снова дали бы
            // position=1, что коллизит с оставшимся игроком (двое на одном
            // месте, на FE второй садится поверх центрального «моего» места).
            player.position = lowestFreePosition()
            players[player.id] = player
        }
    }

    private fun lowestFreePosition(): Int {
        val taken = players.values.mapTo(HashSet()) { it.position }
        var pos = 0
        while (pos in taken) pos++
        return pos
    }

    fun setHoldem() {
        isHoldem = true
    }

    fun setOmaha() {
        isHoldem = false
    }

    fun getIsHoldem(): Boolean = isHoldem

    fun removePlayer(player: PokerPlayer) = players.remove(player.id)

    fun nextPlayerId(): Long = idCounter.incrementAndGet()

    fun alivePlayers(): Long =
        players.values
            .stream()
            .filter { it.isAlive }
            .count()
}
