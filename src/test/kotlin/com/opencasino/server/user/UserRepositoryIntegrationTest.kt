package com.opencasino.server.user

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

@SpringBootTest(
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///userrepotest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:userrepotest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
    ]
)
@ActiveProfiles("test")
class UserRepositoryIntegrationTest {

    @Autowired
    lateinit var users: UserRepository

    @Autowired
    lateinit var identities: UserOAuthIdentityRepository

    @Test
    fun `save then find by email`() {
        val user = User(email = "alice-${UUID.randomUUID()}@example.com", displayName = "alice", balance = 42.50)
        users.save(user).block()

        StepVerifier.create(users.findByEmail(user.email))
            .assertNext { found ->
                assertEquals(user.id, found.id)
                assertEquals(user.email, found.email)
                assertEquals(Role.USER, found.role)
                assertFalse(found.emailVerified)
                assertNull(found.passwordHash)
                assertEquals("alice", found.displayName)
                assertEquals(42.50, found.balance)
            }
            .verifyComplete()
    }

    @Test
    fun `save then find by id`() {
        val user = User(email = "bob-${UUID.randomUUID()}@example.com", displayName = "bob", role = Role.ADMIN)
        users.save(user).block()

        StepVerifier.create(users.findById(user.id))
            .assertNext { found ->
                assertEquals(user.id, found.id)
                assertEquals(Role.ADMIN, found.role)
            }
            .verifyComplete()
    }

    @Test
    fun `findByEmail returns empty when no row matches`() {
        StepVerifier.create(users.findByEmail("ghost-${UUID.randomUUID()}@example.com"))
            .verifyComplete()
    }

    @Test
    fun `deleteById removes the row`() {
        val user = User(email = "dave-${UUID.randomUUID()}@example.com", displayName = "dave")
        users.save(user).block()
        users.deleteById(user.id).block()

        StepVerifier.create(users.findById(user.id))
            .verifyComplete()
    }

    @Test
    fun `save and lookup oauth identity`() {
        val user = User(email = "carol-${UUID.randomUUID()}@example.com", displayName = "carol")
        users.save(user).block()

        val subject = UUID.randomUUID().toString()
        identities.save(UserOAuthIdentity(user.id, "google", subject)).block()

        StepVerifier.create(identities.findByProviderAndSubject("google", subject))
            .assertNext { found ->
                assertEquals(user.id, found.userId)
                assertEquals("google", found.provider)
                assertEquals(subject, found.subject)
            }
            .verifyComplete()
    }

    @Test
    fun `findAllByUserId returns identities for that user only`() {
        val user = User(email = "eve-${UUID.randomUUID()}@example.com", displayName = "eve")
        users.save(user).block()
        val subjectA = UUID.randomUUID().toString()
        val subjectB = UUID.randomUUID().toString()
        identities.save(UserOAuthIdentity(user.id, "google", subjectA)).block()
        identities.save(UserOAuthIdentity(user.id, "github", subjectB)).block()

        StepVerifier.create(identities.findAllByUserId(user.id).collectList())
            .assertNext { list ->
                assertEquals(2, list.size)
                assertEquals(setOf("google", "github"), list.map { it.provider }.toSet())
            }
            .verifyComplete()
    }

    @Test
    fun `lastLoginAt round-trips through repository`() {
        val ts = Instant.parse("2026-05-10T12:34:56Z")
        val user = User(
            email = "frank-${UUID.randomUUID()}@example.com",
            displayName = "frank",
            lastLoginAt = ts,
        )
        users.save(user).block()

        StepVerifier.create(users.findById(user.id))
            .assertNext { found ->
                assertEquals(ts, found.lastLoginAt)
            }
            .verifyComplete()
    }
}
