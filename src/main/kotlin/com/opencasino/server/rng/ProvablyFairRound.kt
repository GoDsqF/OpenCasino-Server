package com.opencasino.server.rng

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table(name = "provably_fair_round")
data class ProvablyFairRound(
    @Id
    @Column("id")
    val id: UUID = UUID.randomUUID(),
    @Column("round_id")
    val roundId: UUID,
    @Column("game_type")
    val gameType: String,
    @Column("server_seed_hash")
    val serverSeedHash: String,
    @Column("revealed_seed")
    val revealedSeed: String? = null,
    @Column("client_seed")
    val clientSeed: String,
    @Column("outcome")
    val outcome: String,
    @Column("house_edge")
    val houseEdge: Double? = null,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
)
