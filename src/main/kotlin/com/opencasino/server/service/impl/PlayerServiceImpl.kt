package com.opencasino.server.service.impl

import com.opencasino.server.game.model.PlayersTable
import com.opencasino.server.service.PlayerService
import org.reactivestreams.Publisher
import org.springframework.data.r2dbc.repository.Query
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class PlayerServiceImpl : PlayerService {

    @Query("select * from players")
    override fun findPlayerById(id: String): Mono<PlayersTable> {
        TODO("Not yet implemented")
    }

    override fun addNewPlayer(player: PlayersTable): Mono<PlayersTable> {
        TODO("Not yet implemented")
    }

    fun findById(id: Publisher<String>): Mono<PlayersTable> {
        TODO("Not yet implemented")
    }

}