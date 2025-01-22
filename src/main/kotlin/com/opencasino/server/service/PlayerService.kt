package com.opencasino.server.service

import com.opencasino.server.game.model.PlayersTable
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*


interface PlayerService: R2dbcRepository<PlayersTable, UUID> {

    fun findPlayerById(id: Mono<UUID>): Mono<PlayersTable>

    fun addNewPlayer(player: PlayersTable): Mono<PlayersTable>

}