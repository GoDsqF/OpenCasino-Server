package com.opencasino.server.service.impl

import com.opencasino.server.config.ApplicationProperties
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
import com.opencasino.server.network.pack.menu.update.PokerRoomSummary
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.network.shared.Message
import com.opencasino.server.service.PokerLobbyService
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.service.shared.FailureCode
import com.opencasino.server.service.shared.WaitingPlayerSession
import com.opencasino.server.user.BalanceLedgerService
import com.opencasino.server.user.UserRepository
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
    private val schedulerService: Scheduler,
    private val ledgerService: BalanceLedgerService,
) : RoomService, PokerLobbyService {

    private lateinit var userRepository: UserRepository
    private lateinit var webSocketSessionService: WebSocketSessionService

    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
    }

    private val gameRoomMap: MutableMap<UUID, PokerGameRoom> = mutableMapOf()
    private val sessionQueue: MutableMap<UUID, Queue<WaitingPlayerSession>> = mutableMapOf()
    override fun getRoomSessionIds(key: UUID?): List<String>? = getRoomByKey(key).map { room ->
        room.sessions().map { it.id }
    }.orElse(emptyList())

    override fun getRoomIds(): Collection<String> = gameRoomMap.keys.map { it.toString() }
    override fun getRooms(): Collection<PokerGameRoom> = gameRoomMap.values.toList()

    override fun listJoinableRooms(): List<PokerRoomSummary> {
        val maxPlayers = applicationProperties.pokerRoom.maxPlayers
        return gameRoomMap.values
            .filter { it.currentPlayersCount() < maxPlayers }
            .map { room ->
                PokerRoomSummary(
                    roomId = room.key().toString(),
                    betType = room.betType.name,
                    bet = room.bet,
                    smallBlind = room.smallBlind,
                    bigBlind = room.bigBlind,
                    currentPlayers = room.currentPlayersCount(),
                    maxPlayers = maxPlayers,
                    phase = if (room.isGameStarted()) "IN_GAME" else "WAITING",
                )
            }
    }

    override fun getRoomByKey(key: UUID?): Optional<GameRoom> =
        if (key != null) Optional.ofNullable(gameRoomMap[key]) else Optional.empty()

    override fun addPlayerToWait(userSession: PlayerSession, initialData: AbstractEvent) {
        when (initialData) {
            is GameRoomCreateEvent -> {
                webSocketSessionService.send(userSession, Message(GAME_ROOM_JOIN_WAIT))
                val gameTable = PokerMap()
                val room = createRoom(gameTable)

                val queue: Queue<WaitingPlayerSession> = ArrayDeque()
                sessionQueue[room.key()] = queue

                val ps: PlayerSession = userSession

                val id = GameRoomJoinEvent(
                    room.gameRoomId.toString(),
                    ps.id
                )

                val player: PokerPlayer = playerFactory.create(gameTable.nextPlayerId(), id, room, ps)

                ps.roomKey = room.key()
                ps.player = player
                ps.serviceId = "Poker"
                queue.add(WaitingPlayerSession(ps, initialData))
                updateSettings(userSession, initialData.settings)

                loadBalanceAndLaunch(ps, player, room)
            }
            is GameRoomJoinEvent -> {
                webSocketSessionService.send(userSession, Message(GAME_ROOM_JOIN_WAIT))
                val room = getRoomByKey(UUID.fromString(initialData.reconnectKey)).get() as PokerGameRoom
                val queue = sessionQueue[room.key()]
                if (queue != null) {
                    val gameTable = room.map
                    val player: PokerPlayer = playerFactory.create(gameTable.nextPlayerId(), initialData, room, userSession)
                    userSession.roomKey = room.key()
                    userSession.player = player
                    userSession.serviceId = "Poker"
                    queue.add(WaitingPlayerSession(userSession, initialData))

                    loadBalanceAndLaunch(userSession, player, room)
                } else {
                    joinRoom(userSession, initialData)
                }
            }
            else -> {
                webSocketSessionService.sendJoinFailure(
                    userSession,
                    FailureCode.INVALID_DECISION,
                    "Unsupported join event: ${initialData::class.simpleName}"
                )
            }
        }
    }

    private fun tryLaunchWaitingRoom(room: PokerGameRoom) {
        val queue = sessionQueue[room.key()] ?: return
        if (queue.size < applicationProperties.pokerRoom.minPlayers) return
        val sessions = queue.map { it.playerSession }
        sessionQueue.remove(room.key())
        launchRoom(room, sessions)
    }

    override fun removePlayerFromWaitQueue(session: PlayerSession) {
        sessionQueue[session.roomKey]?.removeIf { waitingPlayerSession: WaitingPlayerSession -> waitingPlayerSession.playerSession == session }
    }

    private fun createRoom(gameMap: PokerMap): PokerGameRoom {
        val room = PokerGameRoom(gameMap, UUID.randomUUID(), this, webSocketSessionService,
            schedulerService, applicationProperties.game,
            applicationProperties.pokerRoom,
            ledgerService
        )
        gameRoomMap[room.key()] = room
        return room
    }

    private fun joinRoom(userSession: PlayerSession, initialData: GameRoomJoinEvent) {
        val room = getRoomByKey(UUID.fromString(initialData.reconnectKey)).get() as PokerGameRoom
        if (room.currentPlayersCount() >= applicationProperties.pokerRoom.maxPlayers) {
            webSocketSessionService.sendJoinFailure(
                userSession,
                FailureCode.INVALID_DECISION,
                "Room is full"
            )
            return
        }
        val gameTable = room.map
        val player: PokerPlayer = playerFactory.create(gameTable.nextPlayerId(), initialData, room, userSession)

        userSession.roomKey = room.key()
        userSession.player = player
        userSession.serviceId = "Poker"

        val userId = userSession.userId
        val balance = if (userId == null) Mono.just(0.00)
        else userRepository.findById(userId).map { it.balance }.defaultIfEmpty(0.00)
        balance
            .doOnNext { player.balance = it }
            .doOnSuccess { room.addLatePlayer(userSession) }
            .subscribe()
    }

    private fun loadBalanceAndLaunch(ps: PlayerSession, player: PokerPlayer, room: PokerGameRoom) {
        val userId = ps.userId
        val balance = if (userId == null) Mono.just(0.00)
        else userRepository.findById(userId).map { it.balance }.defaultIfEmpty(0.00)
        balance
            .doOnNext { player.balance = it }
            .doOnSuccess { tryLaunchWaitingRoom(room) }
            .subscribe()
    }

    fun updateSettings(userSession: PlayerSession, event: GameSettingsUpdateEvent) {
        val room = getRoomByKey(userSession.roomKey).get() as PokerGameRoom
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