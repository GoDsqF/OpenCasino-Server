package com.opencasino.server.service

import com.opencasino.server.game.model.PlayersTable
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono


interface PlayerService {

    fun findPlayerById(id: String): Mono<PlayersTable>

    fun addNewPlayer(player: PlayersTable): Mono<PlayersTable>

}