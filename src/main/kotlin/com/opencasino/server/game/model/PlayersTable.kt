package com.opencasino.server.game.model

import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@Table(name = "players")
data class PlayersTable(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column("id")
    override val id: UUID = UUID.randomUUID(),

    val username: String,

    @Column("first_name")
    val firstName: String,

    @Column("last_name")
    val lastName: String,

    val balance: Double = 0.00,

    val email: String?,

    @Column("user_hash")
    val userHash: String? = null,

    @Column("created_at")
    val createdAt: Long =
        ZonedDateTime.now(ZoneId.of("Europe/Moscow")).toEpochSecond(),

    @Column("last_modified")
    val lastModified: Long =
        ZonedDateTime.now(ZoneId.of("Europe/Moscow")).toEpochSecond(),
) : Entity<UUID> {
    @Override
    override fun toString(): String {
        return "Player {" +
                "id=$id" +
                ", username='$username'" +
                ", firstName='$firstName'" +
                ", lastName='$lastName'" +
                ", balance=$balance" +
                ", email='$email'" +
                ", userHash='$userHash'" +
                ", createdAt=$createdAt" +
                ", lastModified=$lastModified" +
                "}"
    }
}