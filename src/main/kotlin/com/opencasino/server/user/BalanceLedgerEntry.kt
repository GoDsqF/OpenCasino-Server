package com.opencasino.server.user

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table(name = "balance_ledger")
data class BalanceLedgerEntry(

    @Id
    @Column("id")
    val id: UUID = UUID.randomUUID(),

    @Column("user_id")
    val userId: UUID,

    @Column("round_id")
    val roundId: UUID,

    @Column("delta")
    val delta: Double,

    @Column("reason")
    val reason: BalanceLedgerReason,

    @Column("created_at")
    val createdAt: Instant = Instant.now(),
)