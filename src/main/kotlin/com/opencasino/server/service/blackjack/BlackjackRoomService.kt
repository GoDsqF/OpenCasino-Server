package com.opencasino.server.service.blackjack

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.network.pack.blackjack.shared.BlackjackUserSession
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*


interface BlackjackRoomService {
    fun getRoomSessionIds(key: UUID?): Collection<String>
    fun getRoomIds(): Collection<String>
    fun getRooms(): Collection<BlackjackGameRoom>
    fun getRoomByKey(key: UUID?): Optional<BlackjackGameRoom>
    fun addPlayerToWait(userSession: BlackjackUserSession, initialData: GameRoomJoinEvent)
    fun removePlayerFromWaitQueue(session: BlackjackUserSession)
    fun onRoundEnd(room: BlackjackGameRoom)
    fun close(key: UUID?): Mono<Void>
}