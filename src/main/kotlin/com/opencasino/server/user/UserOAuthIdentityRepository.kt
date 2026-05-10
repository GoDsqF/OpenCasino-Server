package com.opencasino.server.user

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.insert
import org.springframework.data.r2dbc.core.select
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
class UserOAuthIdentityRepository(
    private val template: R2dbcEntityTemplate,
) {

    fun findByProviderAndSubject(provider: String, subject: String): Mono<UserOAuthIdentity> =
        template.select<UserOAuthIdentity>()
            .matching(
                Query.query(
                    Criteria.where("provider").`is`(provider)
                        .and("subject").`is`(subject)
                ).limit(1)
            )
            .one()

    fun findAllByUserId(userId: UUID): Flux<UserOAuthIdentity> =
        template.select<UserOAuthIdentity>()
            .matching(Query.query(Criteria.where("user_id").`is`(userId)))
            .all()

    fun save(identity: UserOAuthIdentity): Mono<UserOAuthIdentity> =
        template.insert<UserOAuthIdentity>().using(identity)
}
