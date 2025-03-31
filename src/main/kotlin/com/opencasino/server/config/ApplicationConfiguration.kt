package com.opencasino.server.config

import com.opencasino.server.network.websocket.MainWebSocketHandler
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

const val WEBSOCKET_PATH = "/"

@EnableWebFlux
@Configuration
@EnableConfigurationProperties(ApplicationProperties::class)
class ApplicationConfiguration(
    private val serverProperties: ApplicationProperties,
    private val handler: MainWebSocketHandler
) {
    @Bean
    @Qualifier(GAME_TASK_MANAGER)
    fun taskManagerService(): Scheduler =
        Schedulers.newParallel(serverProperties.game.gameThreads, Thread.ofVirtual().factory())

    @Bean
    fun webSocketHandlerMapping(): HandlerMapping {
        val map: MutableMap<String, WebSocketHandler> = HashMap()
        map[WEBSOCKET_PATH] = handler
        val handlerMapping = SimpleUrlHandlerMapping()
        handlerMapping.order = 1
        handlerMapping.urlMap = map
        return handlerMapping
    }

    @Bean
    fun authConfiguration(): Auth{
        return authConfiguration()
    }

    @Bean
    fun oauth2Configuration(): OAuth2Properties{
        return oauth2Configuration()
    }

    @Bean
    fun staticRouter(): RouterFunction<ServerResponse> =
        RouterFunctions.resources("/**", ClassPathResource("static/"))
}