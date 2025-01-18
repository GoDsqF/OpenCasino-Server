package com.opencasino.server.service.blackjack.impl

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.GAME_ROOM_JOIN_WAIT
import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.blackjack.factory.BlackjackPlayerFactory
import com.opencasino.server.game.blackjack.map.BlackjackMap
import com.opencasino.server.game.blackjack.model.BlackjackPlayer
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.factory.PlayerFactory
import com.opencasino.server.network.pack.blackjack.shared.BlackjackUserSession
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.PlayerService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.blackjack.BlackjackRoomService
import com.opencasino.server.service.blackjack.BlackjackWebSocketSessionService
import com.opencasino.server.service.blackjack.shared.WaitingBlackjackPlayerSession
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
    private val playerFactory: PlayerFactory<GameRoomJoinEvent, BlackjackPlayer, BlackjackGameRoom, BlackjackUserSession>,
    private val applicationProperties: ApplicationProperties,
    private val schedulerService: Scheduler
) : BlackjackRoomService {

    private lateinit var webSocketSessionService: BlackjackWebSocketSessionService

    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
    }

    private val gameRoomMap: MutableMap<UUID, BlackjackGameRoom> = mutableMapOf()
    private val sessionQueue: Queue<WaitingBlackjackPlayerSession> = ArrayDeque()
    override fun getRoomSessionIds(key: UUID?): Collection<String> = getRoomByKey(key).map { room ->
        room.sessions().map { it.id }
    }.orElse(emptyList())

    override fun getRoomIds(): Collection<String> = gameRoomMap.keys.map { it.toString() }
    override fun getRooms(): Collection<BlackjackGameRoom> = gameRoomMap.values.toList()
    override fun getRoomByKey(key: UUID?): Optional<BlackjackGameRoom> =
        if (key != null) Optional.ofNullable(gameRoomMap[key]) else Optional.empty()

    override fun addPlayerToWait(userSession: BlackjackUserSession, initialData: GameRoomJoinEvent) {
        sessionQueue.add(WaitingBlackjackPlayerSession(userSession, initialData))
        webSocketSessionService.send(userSession, Message(GAME_ROOM_JOIN_WAIT))

        if (sessionQueue.size < applicationProperties.room.maxPlayers) return

        val gameTable = BlackjackMap()
        val room = createRoom(gameTable)
        val userSessions: MutableList<BlackjackUserSession> = ArrayList()
        while (userSessions.size != applicationProperties.room.maxPlayers) {
            val waitingPlayerSession = sessionQueue.remove()
            val ps: BlackjackUserSession = waitingPlayerSession.userSession
            val id: GameRoomJoinEvent = waitingPlayerSession.initialData
            val player: BlackjackPlayer = playerFactory.create(gameTable.nextPlayerId(),  id, room, ps)
            ps.roomKey = room.key()
            ps.player = player
            userSessions.add(ps)
        }
        launchRoom(room, userSessions)
    }

    override fun removePlayerFromWaitQueue(session: BlackjackUserSession) {
        sessionQueue.removeIf{ waitingPlayerSession -> waitingPlayerSession.userSession == session }
    }

    private fun createRoom(gameMap: BlackjackMap): BlackjackGameRoom {
        val room = BlackjackGameRoom(gameMap, UUID.randomUUID(), this, webSocketSessionService,
            schedulerService, applicationProperties.game,
            applicationProperties.room
        )
        gameRoomMap[room.key()] = room
        return room
    }

    private fun launchRoom(room: BlackjackGameRoom, userSessions: List<BlackjackUserSession>) {
        room.onRoomCreated(userSessions)
        room.onRoomStarted()
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
    fun setGameManager(@Lazy webSocketSessionService: BlackjackWebSocketSessionService) {
        this.webSocketSessionService = webSocketSessionService
    }

}