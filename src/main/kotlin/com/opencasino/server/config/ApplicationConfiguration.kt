package com.opencasino.server.config

import com.opencasino.server.network.websocket.MainWebSocketHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers


const val WEBSOCKET_PATH = "/ws"

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
    fun staticRouter(): RouterFunction<ServerResponse> =
        RouterFunctions.resources("/**", ClassPathResource("static/"))
}

@Configuration
// auth.properties is gitignored — only present in local-dev workspaces.
// ignoreResourceNotFound lets Spring silently skip the file when missing
// (CI, fresh clones, prod with env-only config). The bean below already
// returns null-stringified placeholders when the keys are absent, so the
// app starts; populate APP_OAUTH2_CLIENTID / APP_OAUTH2_CLIENTSECRET via
// env to make OAuth actually work.
@PropertySource(value = ["classpath:auth.properties"], ignoreResourceNotFound = true)
@Component
class OAuth2Config() {
    @Autowired
    var environment: Environment? = null

    @Bean
    fun oauth2Configuration(): OAuth2Properties {
        val redirectUris = environment?.getProperty("app.oauth2.redirectUri")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        return OAuth2Properties(
            environment?.getProperty("app.oauth2.clientId").toString(),
            environment?.getProperty("app.oauth2.clientSecret").toString(),
            redirectUris
        )
    }
}
