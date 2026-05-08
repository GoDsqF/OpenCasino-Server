package com.opencasino.server.service.impl

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.GAME_ROOM_JOIN_WAIT
import com.opencasino.server.event.AbstractEvent
import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.blackjack.factory.BlackjackPlayerFactory
import com.opencasino.server.game.blackjack.map.BlackjackMap
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.PlayerRepository
import com.opencasino.server.service.shared.WaitingPlayerSession
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.util.*

@Service
class BlackjackRoomServiceImpl(
    private val playerFactory: BlackjackPlayerFactory,
    private val applicationProperties: ApplicationProperties,
    private val schedulerService: Scheduler
) : RoomService {

    @Autowired
    private lateinit var userRepository: PlayerRepository
    private lateinit var webSocketSessionService: WebSocketSessionService

    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
    }

    private val gameRoomMap: MutableMap<UUID, BlackjackGameRoom> = mutableMapOf()
    private val sessionQueue: Queue<WaitingPlayerSession> = ArrayDeque()
    override fun getRoomSessionIds(key: UUID?): Collection<String> = getRoomByKey(key).map { room ->
        room.sessions().map { it.id }
    }.orElse(emptyList())

    override fun getRoomIds(): Collection<String> = gameRoomMap.keys.map { it.toString() }
    override fun getRooms(): Collection<BlackjackGameRoom> = gameRoomMap.values.toList()
    override fun getRoomByKey(key: UUID?): Optional<GameRoom> =
        if (key != null) Optional.ofNullable(gameRoomMap[key]) else Optional.empty()

    override fun addPlayerToWait(userSession: PlayerSession, initialData: AbstractEvent) {
        sessionQueue.add(WaitingPlayerSession(userSession, initialData as GameRoomJoinEvent))
        webSocketSessionService.send(userSession, Message(GAME_ROOM_JOIN_WAIT))

        if (sessionQueue.size < applicationProperties.blackjackRoom.maxPlayers) return

        val gameTable = BlackjackMap()
        val room = createRoom(gameTable)
        val pending = (0 until applicationProperties.blackjackRoom.maxPlayers).map {
            val waiting = sessionQueue.remove()
            val ps = waiting.playerSession
            val joinEvent = waiting.initialData as GameRoomJoinEvent
            val player: BlackjackPlayer = playerFactory.create(gameTable.nextPlayerId(), joinEvent, room, ps)
            ps.roomKey = room.key()
            ps.player = player
            ps.serviceId = "Blackjack"
            Triple(ps, player, joinEvent.playerUUID)
        }

        Flux.fromIterable(pending)
            .flatMap { (_, player, uuid) ->
                userRepository.findPlayer(uuid)
                    .map { it.balance }
                    .defaultIfEmpty(0.00)
                    .doOnNext { player.balance = it }
            }
            .then()
            .doOnSuccess { launchRoom(room, pending.map { it.first }) }
            .subscribe()
    }

    override fun removePlayerFromWaitQueue(session: PlayerSession) {
        sessionQueue.removeIf{ waitingPlayerSession -> waitingPlayerSession.playerSession == session }
    }

    private fun createRoom(gameMap: BlackjackMap): BlackjackGameRoom {
        val room = BlackjackGameRoom(gameMap, UUID.randomUUID(), this, webSocketSessionService,
            schedulerService, applicationProperties.game,
            applicationProperties.blackjackRoom
        )
        gameRoomMap[room.key()] = room
        return room
    }

    fun launchRoom(room: GameRoom, userSessions: List<PlayerSession>) {
        room.onRoomCreated(userSessions)
        room.onRoomStarted()
        room.onGameStarted()
    }

    override fun onGameEnd(gameRoom: GameRoom) {
        gameRoom.close()
        gameRoomMap.remove(gameRoom.key())
    }

    override fun close(key: UUID?): Mono<Void> {
        getRoomByKey(key).ifPresent {
            onGameEnd(it)
        }
        return Mono.empty()
    }

    @Autowired
    fun setGameManager(@Lazy webSocketSessionService: WebSocketSessionService) {
        this.webSocketSessionService = webSocketSessionService
    }
}