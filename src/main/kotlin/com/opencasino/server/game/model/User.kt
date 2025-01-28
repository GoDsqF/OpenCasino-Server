package com.opencasino.server.game.model

import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Entity
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column("id")
    override val id: String = UUID.randomUUID().toString(),

    @Column("username")
    val username: String,

    @Column("balance")
    val balance: Double = 0.00,

    @Column("first_name")
    val firstName: String,

    @Column("last_name")
    val lastName: String,

    @Column("email")
    val email: String?,

    @Column("user_hash")
    val userHash: String? = null,

    @Column("created_at")
    val createdAt: Long =
        ZonedDateTime.now(ZoneId.of("Europe/Moscow")).toEpochSecond(),

    @Column("last_modified")
    val lastModified: Long =
        ZonedDateTime.now(ZoneId.of("Europe/Moscow")).toEpochSecond(),
) : Player {
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