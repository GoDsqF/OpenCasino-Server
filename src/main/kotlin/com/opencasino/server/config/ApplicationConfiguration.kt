package com.opencasino.server.config

import com.opencasino.server.network.websocket.MainWebSocketHandler
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider.OPTIONS
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions.*
import org.hibernate.dialect.Database
import org.hibernate.dialect.PostgreSQLDialect
import org.springframework.data.r2dbc.dialect.PostgresDialect
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.boot.jdbc.DatabaseDriver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.dialect.R2dbcDialect
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
import javax.sound.sampled.Port
import javax.xml.crypto.Data
import kotlin.properties.Delegates


const val WEBSOCKET_PATH = "/ws"

/*@ConfigurationProperties(prefix = "postgres")
class DatabaseProperties{
    @Value("\${postgres.host}")
    lateinit var host: String

    @Value("\${postgres.port}")
    lateinit var port: String

    @Value("\${postgres.database}")
    lateinit var database: String

    @Value("\${postgres.user}")
    lateinit var user: String

    @Value("\${postgres.password}")
    lateinit var password: String
}*/

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
    fun staticRouter(): RouterFunction<ServerResponse> =
        RouterFunctions.resources("/**", ClassPathResource("static/"))
}

@Configuration
@PropertySource("classpath:auth.properties")
@Component
class OAuth2Config() {
    @Autowired
    var environment: Environment? = null

    @Bean
    fun oauth2Configuration(): OAuth2Properties {
        return OAuth2Properties(
            environment?.getProperty("app.oauth2.clientId").toString(),
            environment?.getProperty("app.oauth2.clientSecret").toString(),
            listOf("http://localhost:3000/oauth2/redirect", "https://opencasino.duckdns.org/oauth2/redirect")
        )
    }
}


@Configuration
@PropertySource("classpath:database.properties")
@Component
class DatabaseConfig {

    @Autowired var environment: Environment? = null

    val options: Map<String, String> = hashMapOf(
        "lock_timeout" to "10s"
    )

    fun getDialect(connectionFactory: ConnectionFactory): R2dbcDialect {
        return org.springframework.data.r2dbc.dialect.MySqlDialect()
    }

    @Bean
    fun connectionFactory(): PostgresqlConnectionFactory {

        /*return PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()

            .host(environment?.getProperty("postgres.host").toString())
            .port(2431)
            .username(environment?.getProperty("postgres.user").toString())
            .password(environment?.getProperty("postgres.password").toString())
            .database(environment?.getProperty("postgres.database").toString())
            .options(options)
            .build()
        )*/


        return PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()

            .host(System.getenv("DATABASE_HOST"))
            .port(System.getenv("DATABASE_PORT").toInt())
            .username(System.getenv("DATABASE_USER"))
            .password(System.getenv("DATABASE_PASSWORD"))
            .database(System.getenv("DATABASE_DB"))
            .options(options)
            .build()

        )


        /*return PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .enableSsl()
                .sslMode(SSLMode.REQUIRE)
                .host(environment?.getProperty("postgres.host").toString())
                .database(environment?.getProperty("postgres.database").toString())
                .username(environment?.getProperty("postgres.user").toString())
                .password(environment?.getProperty("postgres.password").toString())
                .options()
                .build()

        )*/
    }

}