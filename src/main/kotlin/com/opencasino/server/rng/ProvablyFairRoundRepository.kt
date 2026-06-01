package com.opencasino.server.rng

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.insert
import org.springframework.data.r2dbc.core.select
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
class ProvablyFairRoundRepository(
    private val template: R2dbcEntityTemplate,
) {
    fun insert(round: ProvablyFairRound): Mono<ProvablyFairRound> = template.insert<ProvablyFairRound>().using(round)

    fun findByRoundId(roundId: UUID): Mono<ProvablyFairRound> =
        template
            .select<ProvablyFairRound>()
            .matching(Query.query(Criteria.where("round_id").`is`(roundId)))
            .first()

    fun markRevealed(
        roundId: UUID,
        revealedSeed: String,
    ): Mono<Long> =
        template.databaseClient
            .sql("UPDATE provably_fair_round SET revealed_seed = :seed WHERE round_id = :rid")
            .bind("seed", revealedSeed)
            .bind("rid", roundId)
            .fetch()
            .rowsUpdated()
}
