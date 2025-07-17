package com.opencasino.server.service.shared


import com.opencasino.server.config.DatabaseConfig
import com.opencasino.server.game.model.Player
import com.opencasino.server.game.model.Players
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.client.SSLMode
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirst
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query

import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
class PlayerRepository(
    private val template: R2dbcEntityTemplate
) {
    fun findPlayer(id: String): Mono<Players?> = template.select(Players::class.java)
        .matching(
            Query.query(
                Criteria.where("id").`is`(id)
            ).limit(1)
        ).one()
}