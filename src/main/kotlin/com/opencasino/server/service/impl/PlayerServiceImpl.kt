package com.opencasino.server.service.impl

import com.opencasino.server.game.model.PlayersTable
import com.opencasino.server.service.PlayerService
import com.opencasino.server.service.shared.PlayerRepository
import org.reactivestreams.Publisher
import org.springframework.data.r2dbc.repository.Query
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class PlayerServiceImpl(
    private val playerRepository: PlayerRepository,
) : PlayerService {

    @Query("select * from players")
    override fun findPlayerById(id: String): Mono<PlayersTable> {
        return playerRepository.findById(id)
    }

    override fun addNewPlayer(player: PlayersTable): Mono<PlayersTable> {
        return playerRepository.save(player)
    }
}