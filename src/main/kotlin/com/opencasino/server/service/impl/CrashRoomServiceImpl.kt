package com.opencasino.server.service.impl

import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.GAME_ROOM_JOIN_WAIT
import com.opencasino.server.event.AbstractEvent
import com.opencasino.server.event.GameRoomJoinEvent
import com.opencasino.server.game.crash.factory.CrashPlayerFactory
import com.opencasino.server.game.crash.model.CrashPlayer
import com.opencasino.server.game.crash.room.AbstractCrashGameRoom
import com.opencasino.server.game.crash.room.SingleCrashGameRoom
import com.opencasino.server.game.room.GameRoom
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.user.BalanceLedgerService
import com.opencasino.server.user.UserRepository
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.util.Optional
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Подбор/создание Crash-комнат. R3 — только single-режим: комната-на-игрока (CRASH.md
 * §2, §10), зеркало [BlackjackRoomServiceImpl] c `MAX=1`. Очереди нет — каждый join
 * сразу создаёт [SingleCrashGameRoom]. Multi-режим (общая комната на N игроков) —
 * отдельный сервис/ветка в R4.
 */
@Service
class CrashRoomServiceImpl(
    private val playerFactory: CrashPlayerFactory,
    private val applicationProperties: ApplicationProperties,
    private val schedulerService: Scheduler,
    private val randomnessService: RandomnessService,
    private val ledgerService: BalanceLedgerService,
) : RoomService {
    @Autowired
    private lateinit var userRepository: UserRepository
    private lateinit var webSocketSessionService: WebSocketSessionService

    companion object {
        val log: Logger = LogManager.getLogger(this::class.java)
        private const val GUEST_NAME = "guest"
    }

    private val gameRoomMap: MutableMap<UUID, AbstractCrashGameRoom> = mutableMapOf()
    private val playerIdSource = AtomicLong(0)

    override fun getRoomSessionIds(key: UUID?): Collection<String> =
        getRoomByKey(key)
            .map { room -> room.sessions().map { it.id } }
            .orElse(emptyList())

    override fun getRoomIds(): Collection<String> = gameRoomMap.keys.map { it.toString() }

    override fun getRooms(): Collection<GameRoom> = gameRoomMap.values.toList()

    override fun getRoomByKey(key: UUID?): Optional<GameRoom> =
        if (key != null) {
            Optional.ofNullable(gameRoomMap[key])
        } else {
            Optional.empty()
        }

    override fun addPlayerToWait(
        userSession: PlayerSession,
        initialData: AbstractEvent,
    ) {
        webSocketSessionService.send(userSession, Message(GAME_ROOM_JOIN_WAIT))

        val room = createRoom()
        val joinEvent = initialData as GameRoomJoinEvent
        val player = playerFactory.create(playerIdSource.incrementAndGet(), joinEvent, room, userSession) as CrashPlayer
        userSession.roomKey = room.key()
        userSession.player = player
        userSession.serviceId = "Crash"

        val userId = userSession.userId
        val profile =
            if (userId == null) {
                Mono.just(0.00 to GUEST_NAME)
            } else {
                userRepository
                    .findById(userId)
                    .map { it.balance to it.displayName }
                    .defaultIfEmpty(0.00 to GUEST_NAME)
            }
        profile
            .doOnNext { (balance, name) ->
                player.balance = balance
                player.displayName = name
            }.doOnSuccess { launchRoom(room, listOf(userSession)) }
            .subscribe()
    }

    // Очереди нет: single-комната создаётся синхронно на join. Метод нужен интерфейсу
    // (WebSocketSessionService дёргает его на disconnect/leave) — для Crash это no-op.
    override fun removePlayerFromWaitQueue(session: PlayerSession) = Unit

    private fun createRoom(): SingleCrashGameRoom {
        val room =
            SingleCrashGameRoom(
                UUID.randomUUID(),
                schedulerService,
                this,
                webSocketSessionService,
                randomnessService,
                applicationProperties.crashRoom,
                applicationProperties.rng.crash,
                ledgerService,
            )
        gameRoomMap[room.key()] = room
        return room
    }

    private fun launchRoom(
        room: GameRoom,
        userSessions: List<PlayerSession>,
    ) {
        room.onRoomCreated(userSessions)
        room.onRoomStarted()
        room.onGameStarted()
    }

    override fun onGameEnd(gameRoom: GameRoom) {
        gameRoom.close()
        gameRoomMap.remove(gameRoom.key())
    }

    override fun close(key: UUID?): Mono<Void> {
        getRoomByKey(key).ifPresent { onGameEnd(it) }
        return Mono.empty()
    }

    @Autowired
    fun setGameManager(
        @Lazy webSocketSessionService: WebSocketSessionService,
    ) {
        this.webSocketSessionService = webSocketSessionService
    }
}
