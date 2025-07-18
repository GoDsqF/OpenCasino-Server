package com.opencasino.server.service

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.network.shared.PlayerSession
import reactor.core.publisher.Mono
import java.util.*


interface RoomService {
    fun getRoomSessionIds(key: UUID?): Collection<String>
    fun getRoomIds(): Collection<String>
    fun getRooms(): Collection<BlackjackGameRoom>
    fun getRoomByKey(key: UUID?): Optional<BlackjackGameRoom>
    fun addPlayerToWait(userSession: PlayerSession, initialData: GameRoomJoinEvent)
    fun removePlayerFromWaitQueue(session: PlayerSession)
    fun onRoundEnd(room: BlackjackGameRoom)
    fun close(key: UUID?): Mono<Void>
}