package com.opencasino.server.security

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table(name = "refresh_tokens")
data class RefreshToken(

    @Id
    @Column("id")
    val id: UUID = UUID.randomUUID(),

    @Column("user_id")
    val userId: UUID,

    @Column("token_hash")
    val tokenHash: String,

    @Column("created_at")
    val createdAt: Instant,

    @Column("expires_at")
    val expiresAt: Instant,

    @Column("revoked_at")
    val revokedAt: Instant? = null,

    @Column("user_agent")
    val userAgent: String? = null,

    @Column("ip")
    val ip: String? = null,
)