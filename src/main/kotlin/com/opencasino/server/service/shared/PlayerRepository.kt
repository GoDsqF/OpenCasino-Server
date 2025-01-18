package com.opencasino.server.service.shared

import com.opencasino.server.game.model.PlayersTable
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface PlayerRepository: R2dbcRepository<PlayersTable, String> {
    fun findPlayerById(id: String): Mono<PlayersTable>
}