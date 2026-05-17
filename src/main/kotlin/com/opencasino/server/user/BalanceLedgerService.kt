package com.opencasino.server.user

import org.apache.logging.log4j.LogManager
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.util.UUID

@Service
class BalanceLedgerService(
    private val ledgerRepository: BalanceLedgerRepository,
    private val template: R2dbcEntityTemplate,
    private val transactionalOperator: TransactionalOperator,
) {

    companion object {
        private val log = LogManager.getLogger(BalanceLedgerService::class.java)
    }

    fun applyDelta(
        userId: UUID,
        roundId: UUID,
        delta: Double,
        reason: BalanceLedgerReason,
    ): Mono<BalanceLedgerEntry> {
        val entry = BalanceLedgerEntry(
            userId = userId,
            roundId = roundId,
            delta = delta,
            reason = reason,
        )
        return ledgerRepository.insert(entry)
            .flatMap { saved ->
                updateUserBalance(userId, delta).thenReturn(saved)
            }
            .`as`(transactionalOperator::transactional)
            .doOnError { e -> log.error("balance ledger apply failed user={} round={} delta={} reason={}", userId, roundId, delta, reason, e) }
    }

    private fun updateUserBalance(userId: UUID, delta: Double): Mono<Long> {
        if (delta == 0.0) return Mono.just(0L)
        return template.databaseClient
            .sql("UPDATE users SET balance = balance + :delta, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
            .bind("delta", delta)
            .bind("id", userId)
            .fetch()
            .rowsUpdated()
    }
}