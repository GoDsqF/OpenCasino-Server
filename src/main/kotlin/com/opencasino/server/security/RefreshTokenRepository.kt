package com.opencasino.server.security

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.insert
import org.springframework.data.r2dbc.core.select
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

@Repository
class RefreshTokenRepository(
    private val template: R2dbcEntityTemplate,
) {

    fun findByTokenHash(tokenHash: String): Mono<RefreshToken> =
        template.select<RefreshToken>()
            .matching(Query.query(Criteria.where("token_hash").`is`(tokenHash)).limit(1))
            .one()

    fun save(token: RefreshToken): Mono<RefreshToken> =
        template.insert<RefreshToken>().using(token)

    fun markRevoked(id: UUID, revokedAt: Instant): Mono<Long> =
        template.update(RefreshToken::class.java)
            .matching(
                Query.query(
                    Criteria.where("id").`is`(id)
                        .and("revoked_at").isNull
                )
            )
            .apply(Update.update("revoked_at", revokedAt))

    fun revokeAllForUser(userId: UUID, revokedAt: Instant): Mono<Long> =
        template.update(RefreshToken::class.java)
            .matching(
                Query.query(
                    Criteria.where("user_id").`is`(userId)
                        .and("revoked_at").isNull
                )
            )
            .apply(Update.update("revoked_at", revokedAt))
}