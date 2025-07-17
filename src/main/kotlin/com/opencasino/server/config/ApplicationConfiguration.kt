package com.opencasino.server.config

import com.opencasino.server.network.websocket.MainWebSocketHandler
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.client.SSLMode
import io.r2dbc.spi.ConnectionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
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

    /*@Bean
    fun authConfiguration(): Auth{
        return authConfiguration()
    }*/

    @Bean
    fun oauth2Configuration(): OAuth2Properties{
        return OAuth2Properties()
    }

    @Bean
    fun staticRouter(): RouterFunction<ServerResponse> =
        RouterFunctions.resources("/**", ClassPathResource("static/"))
}

@Configuration
@EnableR2dbcRepositories
@PropertySource("classpath:database.properties")
@Component
class DatabaseConfig() : AbstractR2dbcConfiguration() {

    @Autowired var environment: Environment? = null

    override fun connectionFactory(): ConnectionFactory {

        return PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .enableSsl()
                .sslMode(SSLMode.REQUIRE)
                .host(environment?.getProperty("postgres.host").toString())
                .database(environment?.getProperty("postgres.database").toString())
                .username(environment?.getProperty("postgres.user").toString())
                .password(environment?.getProperty("postgres.password").toString())
                .build()
        )
    }
}