package com.opencasino.server.service

import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.model.Player
import com.opencasino.server.network.shared.PlayerSession
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*

@Service
interface RoomService<GR> {
    fun getRoomSessionIds(key: UUID?): Collection<String>
    fun getRoomIds(): Collection<String>
    fun getRooms(): Collection<GR>
    fun getRoomByKey(key: UUID?): Optional<GR>
    fun addPlayerToWait(playerSession: PlayerSession<Player>, initialData: GameRoomJoinEvent)
    fun removePlayerFromWaitQueue(session: PlayerSession<Player>)
    fun onRoundEnd(room: GR)
    fun close(key: UUID?): Mono<Void>
}