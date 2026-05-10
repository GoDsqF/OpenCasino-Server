package com.opencasino.server.game.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@Table(name = "players")
data class Players(

    @Id
    @Column("id")
    val id: String = UUID.randomUUID().toString(),

    @Column("user_id")
    val userId: UUID,

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

    @Column("created_at")
    val createdAt: Long =
        ZonedDateTime.now(ZoneId.of("Europe/Moscow")).toEpochSecond(),

    @Column("last_modified")
    val lastModified: Long =
        ZonedDateTime.now(ZoneId.of("Europe/Moscow")).toEpochSecond(),
) {
    override fun toString(): String {
        return "Player {" +
                "id=$id" +
                ", userId=$userId" +
                ", username='$username'" +
                ", firstName='$firstName'" +
                ", lastName='$lastName'" +
                ", balance=$balance" +
                ", email='$email'" +
                ", createdAt=$createdAt" +
                ", lastModified=$lastModified" +
                "}"
    }
}
