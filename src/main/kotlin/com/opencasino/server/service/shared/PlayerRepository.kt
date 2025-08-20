package com.opencasino.server.service.shared


import com.opencasino.server.config.DatabaseConfig
import com.opencasino.server.game.model.Player
import com.opencasino.server.game.model.Players
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.client.SSLMode
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.collect
import org.reactivestreams.Publisher
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.data.relational.core.dialect.Dialect
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.r2dbc.core.await
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Component

import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@EnableR2dbcRepositories("opencasino.repository")
@Component
class PlayerRepository {

    val mono = DatabaseConfig().connectionFactory().create()

    fun findPlayer(id: String): Flux<Players?> = mono.flatMapMany { connection ->
        connection
            .createStatement("SELECT players.* FROM players WHERE players.id = $1 LIMIT 1")
            .bind("$1", id)
            .execute()
    }.flatMap { result ->
        result.map { row, rowMetadata ->
            PlayerMapper().apply(row, rowMetadata)
        }
    }

}