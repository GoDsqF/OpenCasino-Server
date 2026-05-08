package com.opencasino.server.service.shared

import com.opencasino.server.game.model.Players
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.select
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
class PlayerRepository(
    private val template: R2dbcEntityTemplate
) {

    fun findPlayer(id: String): Mono<Players> =
        template.select<Players>()
            .matching(Query.query(Criteria.where("id").`is`(id)).limit(1))
            .one()
}
