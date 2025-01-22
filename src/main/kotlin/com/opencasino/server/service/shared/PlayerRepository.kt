package com.opencasino.server.service.shared

import com.opencasino.server.game.model.PlayersTable
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.*

@Repository
interface PlayerRepository: R2dbcRepository<PlayersTable, String> {
    fun findPlayerById(id: Mono<UUID>): Mono<PlayersTable>

    fun addNewPlayer(player: PlayersTable): Mono<PlayersTable>
}