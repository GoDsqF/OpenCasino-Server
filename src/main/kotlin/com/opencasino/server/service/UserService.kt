package com.opencasino.server.service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.stereotype.Service


@SpringBootApplication
@Service
class UserService {
/*
    val repository = UserRepository(R2dbcEntityTemplate(DatabaseConfig().connectionFactory()))

    fun selectBalanceById(id: UUID): Double {
        return repository.findPlayer(id.toString())?.balance ?: 0.00
    }*/

    /*val connectionFactory = DatabaseConfig().connectionFactory()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun selectBalanceById(id: UUID): Double {
        var result: Double = 0.00
        connectionFactory.create().asFlow()
            .map { it.createStatement("SELECT balance from players where id = :id") }
            .onEach { it.bind(":id", id) }
            .flatMapConcat { it.execute().asFlow() }
            .flatMapConcat {
                it.map { row, metadata ->
                    row.get("balance", Double::class.java)!!
                }.asFlow()
            }.collect { value ->
                if (value != null) {
                    result = value
                }
            }
        return result
    }*/


}