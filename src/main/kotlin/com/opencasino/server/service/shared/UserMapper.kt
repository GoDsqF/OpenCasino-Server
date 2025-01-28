package com.opencasino.server.service.shared

import com.opencasino.server.game.model.Users
import io.r2dbc.spi.Row
import org.springframework.stereotype.Component
import java.util.*
import java.util.function.BiFunction

@Component
class UserMapper : BiFunction<Row, Any, Users> {
    override fun apply(row: Row, any: Any): Users {
        return Users(
            row.get("id", UUID::class.java) ?: UUID.fromString("00000000-0000-0000-0000-000000000000"),
            row.get("username", String::class.java) ?: "",
            row.get("balance", Double::class.java) ?: 0.00,
            row.get("firstName", String::class.java) ?: "",
            row.get("lastName", String::class.java) ?: "",
            row.get("email", String::class.java) ?: "",
            row.get("userhash", String::class.java) ?: "",
            row.get("createdAt", Long::class.java) ?: 0,
            row.get("lastModified", Long::class.java) ?: 0,

        )
    }
}