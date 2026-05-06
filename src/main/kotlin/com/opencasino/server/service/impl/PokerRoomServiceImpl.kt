package com.opencasino.server.service.impl

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.FAILURE
import com.opencasino.server.config.GAME_ROOM_JOIN_WAIT
import com.opencasino.server.event.AbstractEvent
import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.event.poker.GameRoomCreateEvent
import com.opencasino.server.event.poker.GameSettingsUpdateEvent
import com.opencasino.server.game.poker.holdem.factory.PokerPlayerFactory
import com.opencasino.server.game.poker.holdem.map.PokerMap
import com.opencasino.server.game.poker.holdem.model.PokerBetType
import com.opencasino.server.game.poker.holdem.model.PokerPlayer
import com.opencasino.server.game.poker.holdem.room.PokerGameRoom
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.PlayerRepository
import com.opencasino.server.service.shared.WaitingPlayerSession
import kotlinx.coroutines.NonCancellable.onJoin
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.util.*

@Service
class PokerRoomServiceImpl(
    private val playerFactory: PokerPlayerFactory,
    private val applicationProperties: ApplicationProperties,
    private val schedulerService: Scheduler
) : RoomService {

    @Autowired
    private lateinit var userRepository: PlayerRepository
    private lateinit var webSocketSessionService: WebSocketSessionService

    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
    }

    private val gameRoomMap: MutableMap<UUID, PokerGameRoom> = mutableMapOf()
    private val sessionQueue: Map<UUID, Queue<WaitingPlayerSession>> = mapOf()
    override fun getRoomSessionIds(key: UUID?): Collection<String> = getRoomByKey(key).map { room ->
        room.sessions().map { it.id }
    }.orElse(emptyList())

    override fun getRoomIds(): Collection<String> = gameRoomMap.keys.map { it.toString() }
    override fun getRooms(): Collection<PokerGameRoom> = gameRoomMap.values.toList()
    override fun getRoomByKey(key: UUID?): Optional<PokerGameRoom> =
        if (key != null) Optional.ofNullable(gameRoomMap[key]) else Optional.empty()

    override fun addPlayerToWait(userSession: PlayerSession, initialData: AbstractEvent) {
        when (initialData) {
            is GameRoomCreateEvent -> {
                webSocketSessionService.send(userSession, Message(GAME_ROOM_JOIN_WAIT))
                val gameTable = PokerMap()
                val room = createRoom(gameTable)

                sessionQueue.plus(room.key() to WaitingPlayerSession(userSession, initialData))

                val ps: PlayerSession = userSession

                val id = GameRoomJoinEvent(
                    room.gameRoomId.toString(),
                    ps.id
                )

                val player: PokerPlayer = playerFactory.create(gameTable.nextPlayerId(), id, room, ps)

                userRepository.findPlayer(initialData.playerUUID).subscribe { user ->
                    if (user != null) {
                        player.balance = user.balance
                    }
                    else player.balance = 0.00
                }

                ps.roomKey = room.key()
                ps.player = player
                ps.serviceId = "Poker"

                launchRoom(room, listOf(ps))
                updateSettings(userSession, initialData.settings)
            }
            is GameRoomJoinEvent -> {
                webSocketSessionService.send(userSession, Message(GAME_ROOM_JOIN_WAIT))
                val room = getRoomByKey(UUID.fromString(initialData.reconnectKey)).get()
                sessionQueue.plus(room.key() to WaitingPlayerSession(userSession, initialData))
                joinRoom(userSession, initialData)
            }
            else -> {
                webSocketSessionService.send(userSession, Message(FAILURE))
            }
        }
    }

    /*override fun addPlayerToWait(userSession: PlayerSession, initialData: GameRoomJoinEvent) {
        sessionQueue.add(WaitingPlayerSession(userSession, initialData))
        webSocketSessionService.send(userSession, Message(GAME_ROOM_JOIN_WAIT))

        if (sessionQueue.size < applicationProperties.pokerRoom.minPlayers) return

        val gameTable = PokerMap()
        val room = createRoom(gameTable)
        val userSessions: MutableList<PlayerSession> = ArrayList()
        // TODO("FIX THIS LATER(BETTER SOON)")
        // This part supposed to let players join the room until max players count is reached or start command from room creator received\
        //changed: room should exist on create call and players can join if room is not full
        while (userSessions.size != applicationProperties.pokerRoom.minPlayers) {
            val waitingPlayerSession = sessionQueue.remove()
            val ps: PlayerSession = waitingPlayerSession.playerSession
            val id: GameRoomJoinEvent = waitingPlayerSession.initialData
            val player: PokerPlayer = playerFactory.create(gameTable.nextPlayerId(), id, room, ps)
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
    }*/

    override fun removePlayerFromWaitQueue(session: PlayerSession) {
        sessionQueue[session.roomKey]?.removeIf { waitingPlayerSession: WaitingPlayerSession -> waitingPlayerSession.playerSession == session }
    }

    private fun createRoom(gameMap: PokerMap): PokerGameRoom {
        val room = PokerGameRoom(gameMap, UUID.randomUUID(), this, webSocketSessionService,
            schedulerService, applicationProperties.game,
            applicationProperties.pokerRoom
        )
        gameRoomMap[room.key()] = room
        return room
    }

    private fun joinRoom(userSession: PlayerSession, initialData: GameRoomJoinEvent) {
        val room = getRoomByKey(UUID.fromString(initialData.reconnectKey)).get()
        val gameTable = room.map
        val player: PokerPlayer = playerFactory.create(gameTable.nextPlayerId(), initialData, room, userSession)

        userRepository.findPlayer(initialData.playerUUID).subscribe { user ->
            println(user)
            if (user != null) {
                player.balance = user.balance
            }
            //Return balance message
            else player.balance = 0.00
        }

        userSession.roomKey = room.key()
        userSession.player = player
        userSession.serviceId = "Poker"
        onJoinedPlayer(room, userSession)
    }

    fun updateSettings(userSession: PlayerSession, event: GameSettingsUpdateEvent) {
        val room = getRoomByKey(userSession.roomKey).get()
        room.betType = event.betType?.let { PokerBetType.valueOf(it) }!!
        room.minLimit = event.minLimit
        room.maxLimit = event.maxLimit
        room.bet = event.bet
    }

    fun launchRoom(room: GameRoom, userSessions: List<PlayerSession>) {
        room.onRoomCreated(userSessions)
        room.onRoomStarted()
        room.onGameStarted()
    }

    fun onJoinedPlayer(room: GameRoom, userSession: PlayerSession) {
        room.sessions().plus(userSession)
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