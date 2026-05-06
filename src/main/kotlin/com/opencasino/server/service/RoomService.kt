package com.opencasino.server.service

import com.opencasino.server.event.AbstractEvent
import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.network.shared.PlayerSession
import reactor.core.publisher.Mono
import java.util.*


interface RoomService {
    fun getRoomSessionIds(key: UUID?): Collection<String>
    fun getRoomIds(): Collection<String>
    fun getRooms(): Collection<Any>
    fun getRoomByKey(key: UUID?): Any?
    fun addPlayerToWait(userSession: PlayerSession, initialData: AbstractEvent)
    fun removePlayerFromWaitQueue(session: PlayerSession)
    fun close(key: UUID?): Mono<Void>
    fun onGameEnd(gameRoom: GameRoom)
}
