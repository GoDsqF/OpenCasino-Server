package com.opencasino.server.service.shared

import com.opencasino.server.game.model.Players
import io.r2dbc.spi.Row
import org.springframework.stereotype.Component
import java.util.*
import java.util.function.BiFunction

@Component
class PlayerMapper : BiFunction<Row, Any, Players> {
    override fun apply(row: Row, any: Any): Players {
        return Players(
            row.get("id", String::class.java) ?: "00000000-0000-0000-0000-000000000000",
            row.get("username", String::class.java) ?: "",
            row.get("balance", Double::class.java) ?: 0.00,
            row.get("first_name", String::class.java) ?: "",
            row.get("last_name", String::class.java) ?: "",
            row.get("email", String::class.java) ?: "",
            row.get("user_hash", String::class.java) ?: "",
            row.get("created_at", Long::class.java) ?: 0L,
            row.get("last_modified", Long::class.java) ?: 0L,
        )
    }
}