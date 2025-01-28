package com.opencasino.server.service

import com.opencasino.server.game.model.Users
import com.opencasino.server.game.model.UserData
import com.opencasino.server.service.shared.UserRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*

@Service
class UserService(
    private val db: UserRepository
) {

    fun findUserById(id: String): Mono<Users> = db.findById(UUID.fromString(id))

    fun addNewUser(player: UserData): Mono<Users> {
        val newUser = Users(
            username = player.username,
            firstName = player.firstName,
            lastName = player.lastName,
            email = player.email
        )
        return db.save(newUser)
    }
}