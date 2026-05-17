package com.opencasino.server.user

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.insert
import org.springframework.data.r2dbc.core.select
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
class BalanceLedgerRepository(
    private val template: R2dbcEntityTemplate,
) {

    fun insert(entry: BalanceLedgerEntry): Mono<BalanceLedgerEntry> =
        template.insert<BalanceLedgerEntry>().using(entry)

    fun findByUserId(userId: UUID): Flux<BalanceLedgerEntry> =
        template.select<BalanceLedgerEntry>()
            .matching(Query.query(Criteria.where("user_id").`is`(userId)))
            .all()

    fun findByRoundId(roundId: UUID): Flux<BalanceLedgerEntry> =
        template.select<BalanceLedgerEntry>()
            .matching(Query.query(Criteria.where("round_id").`is`(roundId)))
            .all()
}