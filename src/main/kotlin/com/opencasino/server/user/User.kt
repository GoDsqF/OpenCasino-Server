package com.opencasino.server.user

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table(name = "users")
data class User(

    @Id
    @Column("id")
    val id: UUID = UUID.randomUUID(),

    @Column("email")
    val email: String,

    @Column("email_verified")
    val emailVerified: Boolean = false,

    @Column("password_hash")
    val passwordHash: String? = null,

    @Column("role")
    val role: Role = Role.USER,

    @Column("created_at")
    val createdAt: Instant = Instant.now(),

    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),

    @Column("last_login_at")
    val lastLoginAt: Instant? = null,
)
