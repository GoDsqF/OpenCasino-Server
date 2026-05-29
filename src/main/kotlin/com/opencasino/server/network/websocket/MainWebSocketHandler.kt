package com.opencasino.server.network.websocket

import com.google.gson.GsonBuilder
import com.opencasino.server.config.ApplicationProperties
import com.opencasino.server.config.HEARTBEAT_SCHEDULER
import com.opencasino.server.config.PING
import com.opencasino.server.network.shared.Message
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.io.InputStreamReader
import java.time.Duration
import java.util.concurrent.TimeoutException

@Service
class MainWebSocketHandler(
    private val webSocketSessionService: WebSocketSessionService,
    private val applicationProperties: ApplicationProperties,
) : WebSocketHandler {
    // serializeNulls: контракт (opencasino-docs api/types.ts) объявляет nullable-поля
    // паков как `T | null`. Дефолтный Gson выкидывает null-ключи из JSON, и клиент
    // получает undefined вместо null — строгие `field === null` проверки на FE
    // принимают отсутствующий ключ за non-null и падают на дереференсе. Явные null
    // приводят провод в соответствие с задекларированным контрактом.
    private val objectMapper = GsonBuilder().serializeNulls().create()
    private lateinit var blackjackRoomService: RoomService
    private lateinit var pokerRoomService: RoomService
    private lateinit var heartbeatScheduler: Scheduler

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        private const val SUB_PROTOCOL_BEARER = "bearer"
    }

    fun overrideHeartbeatSchedulerForTests(scheduler: Scheduler) {
        heartbeatScheduler = scheduler
    }

    override fun getSubProtocols(): List<String> = listOf(SUB_PROTOCOL_BEARER)

    override fun handle(webSocketSession: WebSocketSession): Mono<Void> {
        val input = webSocketSession.receive().share()
        val userSession = PlayerSession(webSocketSession.id, webSocketSession.handshakeInfo)
        val sessionHandler =
            UserSessionWebSocketHandler(
                userSession,
                webSocketSessionService,
                blackjackRoomService,
                pokerRoomService,
            )

        val heartbeat = applicationProperties.heartbeat
        val guarded: Flux<WebSocketMessage> =
            if (heartbeat.enabled) {
                val livenessWindow: Duration = heartbeat.interval.plus(heartbeat.pongTimeout)
                input.timeout(livenessWindow, heartbeatScheduler)
            } else {
                input
            }

        val receive =
            guarded
                .filter { it.type == WebSocketMessage.Type.TEXT }
                // Парсинг и onNext изолированы per-frame: исключение на одном
                // входящем (битый JSON, бросок в onNext) логируется и дропает
                // только этот фрейм, а не рвёт весь сокет. Иначе единичный кривой
                // фрейм валил Flux.merge → doOnTerminate(onInactive) → 60s grace
                // → стол «сам закрывался». TimeoutException сюда не попадает: он
                // приходит сверху от guarded и ловится внешним onErrorResume ниже.
                .concatMap { frame ->
                    Mono
                        .fromCallable { toMessage(frame) }
                        .doOnNext(sessionHandler::onNext)
                        .onErrorResume { e ->
                            log.warn("dropping inbound frame on session {}", webSocketSession.id, e)
                            Mono.empty()
                        }
                }
                .onErrorResume(TimeoutException::class.java) {
                    log.debug("pong-timeout on session {}", webSocketSession.id)
                    webSocketSession
                        .close(
                            CloseStatus.SERVER_ERROR.withReason("pong-timeout"),
                        ).then(Mono.empty<Message>())
                }

        val sessionOutbound: Flux<Any> = webSocketSessionService.onActive(userSession)
        val outbound: Flux<Any> =
            if (heartbeat.enabled) {
                Flux.merge(sessionOutbound, heartbeatPings(heartbeat.interval))
            } else {
                sessionOutbound
            }

        val send =
            webSocketSession.send(
                outbound
                    .map { webSocketSession.textMessage(objectMapper.toJson(it)) }
                    .doOnError { handleError(webSocketSession, it) },
            )

        val security =
            webSocketSession.handshakeInfo.principal.doOnNext {
                webSocketSessionService.onPrincipalInit(userSession, it)
            }

        return Flux
            .merge(receive, send, security)
            .doOnSubscribe { webSocketSessionService.onSubscribe(userSession, it) }
            .doOnTerminate { webSocketSessionService.onInactive(userSession) }
            .doOnError { handleError(webSocketSession, it) }
            .then()
    }

    internal fun heartbeatPings(interval: Duration): Flux<Any> =
        Flux
            .interval(interval, interval, heartbeatScheduler)
            .map<Any> { Message(PING) }

    private fun toMessage(webSocketMessage: WebSocketMessage): Message {
        val message = objectMapper.fromJson(InputStreamReader(webSocketMessage.payload.asInputStream()), Message::class.java)
        log.trace("inbound {}", message)
        return message
    }

    private fun handleError(
        webSocketSession: WebSocketSession,
        exception: Throwable,
    ) {
        log.error("Error in ${webSocketSession.id} session", exception)
    }

    @Autowired
    @Qualifier("blackjackRoomServiceImpl")
    fun setGameRoomManagementServices(
        @Lazy roomService: RoomService,
    ) {
        this.blackjackRoomService = roomService
    }

    @Autowired
    @Qualifier("pokerRoomServiceImpl")
    fun setPokerRoomManagementService(
        @Lazy roomService: RoomService,
    ) {
        this.pokerRoomService = roomService
    }

    @Autowired
    @Qualifier(HEARTBEAT_SCHEDULER)
    fun setHeartbeatScheduler(
        @Lazy scheduler: Scheduler,
    ) {
        this.heartbeatScheduler = scheduler
    }
}
