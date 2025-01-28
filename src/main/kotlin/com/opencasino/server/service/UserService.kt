package com.opencasino.server.service

import com.opencasino.server.game.model.User
import com.opencasino.server.game.model.UserData
import com.opencasino.server.service.shared.UserRepository
import org.springframework.boot.autoconfigure.security.SecurityProperties
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*

@Service
class UserService(
    private val db: UserRepository
) {

    fun findPlayerById(id: String): Mono<User> = db.findById(UUID.fromString(id))

    fun addNewPlayer(player: UserData): Mono<User> {
        val newUser = SecurityProperties.User(

        )
    }

    fun updatePlayer(id: String, player: User): Mono<User>
}