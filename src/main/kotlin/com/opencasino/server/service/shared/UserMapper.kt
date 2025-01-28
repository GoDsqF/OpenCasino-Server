package com.opencasino.server.service.shared

import com.opencasino.server.game.model.User
import io.r2dbc.spi.Row
import org.springframework.stereotype.Component
import java.util.function.BiFunction

@Component
class UserMapper : BiFunction<Row, Any, User> {
    override fun apply(row: Row, any: Any): User {
        return User(
            row.get("id", String::class.java) ?: "",
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