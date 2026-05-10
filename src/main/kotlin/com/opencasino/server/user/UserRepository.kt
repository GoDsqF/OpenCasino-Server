package com.opencasino.server.user

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.delete
import org.springframework.data.r2dbc.core.insert
import org.springframework.data.r2dbc.core.select
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
class UserRepository(
    private val template: R2dbcEntityTemplate,
) {

    fun findById(id: UUID): Mono<User> =
        template.select<User>()
            .matching(Query.query(Criteria.where("id").`is`(id)).limit(1))
            .one()

    fun findByEmail(email: String): Mono<User> =
        template.select<User>()
            .matching(Query.query(Criteria.where("email").`is`(email)).limit(1))
            .one()

    fun save(user: User): Mono<User> =
        template.insert<User>().using(user)

    fun deleteById(id: UUID): Mono<Long> =
        template.delete<User>()
            .matching(Query.query(Criteria.where("id").`is`(id)))
            .all()
}
