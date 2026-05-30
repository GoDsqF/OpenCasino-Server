package com.opencasino.server.rng

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest(
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///pfrtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:pfrtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
    ],
)
@ActiveProfiles("test")
class ProvablyFairRoundRepositoryIntegrationTest {
    @Autowired lateinit var repository: ProvablyFairRoundRepository

    private fun newRound(roundId: UUID): ProvablyFairRound =
        ProvablyFairRound(
            roundId = roundId,
            gameType = "CRASH",
            serverSeedHash = "a".repeat(64),
            clientSeed = "client-seed",
            outcome = "3.47",
            houseEdge = 0.03,
        )

    @Test
    fun `insert then findByRoundId returns row with null revealed seed`() {
        val roundId = UUID.randomUUID()
        repository.insert(newRound(roundId)).block()

        val found = repository.findByRoundId(roundId).block()!!
        assertEquals("CRASH", found.gameType)
        assertEquals("3.47", found.outcome)
        assertEquals(0.03, found.houseEdge)
        assertNull(found.revealedSeed)
    }

    @Test
    fun `markRevealed sets the revealed seed`() {
        val roundId = UUID.randomUUID()
        repository.insert(newRound(roundId)).block()

        val rows = repository.markRevealed(roundId, "b".repeat(64)).block()!!
        assertEquals(1L, rows)

        val found = repository.findByRoundId(roundId).block()!!
        assertEquals("b".repeat(64), found.revealedSeed)
    }
}
