package com.opencasino.server.service.impl

import com.opencasino.server.game.model.User
import com.opencasino.server.game.model.UserData
import com.opencasino.server.service.UserService
import com.opencasino.server.service.shared.UserMapper
import org.springframework.data.repository.query.Param
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*

@Service
class PlayerServiceImpl(
    private val databaseClient: DatabaseClient,
    private val mapper: UserMapper
) : UserService {

    override fun findPlayerById(id: String): Mono<User> {
        return playerRepository.findById(UUID.fromString(id))
    }

    override fun addNewPlayer(@Param("player") player: UserData): Mono<User> {

    }

    override fun updatePlayer(id: String, player: User): Mono<User> {

    }
}