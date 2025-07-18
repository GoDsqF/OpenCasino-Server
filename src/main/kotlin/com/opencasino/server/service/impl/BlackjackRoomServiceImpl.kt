package com.opencasino.server.service.impl

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.GAME_ROOM_JOIN_WAIT
import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.blackjack.map.BlackjackMap
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.factory.PlayerFactory
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
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.util.*

@Service
class BlackjackRoomServiceImpl(
    private val playerFactory: PlayerFactory<GameRoomJoinEvent, BlackjackPlayer, BlackjackGameRoom, PlayerSession>,
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
    override fun getRoomByKey(key: UUID?): Optional<BlackjackGameRoom> =
        if (key != null) Optional.ofNullable(gameRoomMap[key]) else Optional.empty()

    override fun addPlayerToWait(userSession: PlayerSession, initialData: GameRoomJoinEvent) {
        sessionQueue.add(WaitingPlayerSession(userSession, initialData))
        webSocketSessionService.send(userSession, Message(GAME_ROOM_JOIN_WAIT))

        if (sessionQueue.size < applicationProperties.room.maxPlayers) return

        val gameTable = BlackjackMap()
        val room = createRoom(gameTable)
        val userSessions: MutableList<PlayerSession> = ArrayList()
        while (userSessions.size != applicationProperties.room.maxPlayers) {
            val waitingPlayerSession = sessionQueue.remove()
            val ps: PlayerSession = waitingPlayerSession.playerSession
            val id: GameRoomJoinEvent = waitingPlayerSession.initialData
            val player: BlackjackPlayer = playerFactory.create(gameTable.nextPlayerId(), id, room, ps)
            userRepository.findPlayer(initialData.playerUUID).subscribe { user ->
                if (user != null) {
                    player.balance = user.balance
                }
                else player.balance = 0.00
            }
            ps.roomKey = room.key()
            ps.player = player
            userSessions.add(ps)
        }
        launchRoom(room, userSessions)
    }

    override fun removePlayerFromWaitQueue(session: PlayerSession) {
        sessionQueue.removeIf{ waitingPlayerSession -> waitingPlayerSession.playerSession == session }
    }

    private fun createRoom(gameMap: BlackjackMap): BlackjackGameRoom {
        val room = BlackjackGameRoom(gameMap, UUID.randomUUID(), this, webSocketSessionService,
            schedulerService, applicationProperties.game,
            applicationProperties.room
        )
        gameRoomMap[room.key()] = room
        return room
    }

    private fun launchRoom(room: BlackjackGameRoom, userSessions: List<PlayerSession>) {
        room.onRoomCreated(userSessions)
        room.onRoomStarted()
        room.onGameStarted()
    }

    override fun onRoundEnd(room: BlackjackGameRoom) {
        room.close()
        gameRoomMap.remove(room.key())
    }

    override fun close(key: UUID?): Mono<Void> {
        getRoomByKey(key).ifPresent {
            onRoundEnd(it)
        }
        return Mono.empty()
    }

    @Autowired
    fun setGameManager(@Lazy webSocketSessionService: WebSocketSessionService) {
        this.webSocketSessionService = webSocketSessionService
    }

}