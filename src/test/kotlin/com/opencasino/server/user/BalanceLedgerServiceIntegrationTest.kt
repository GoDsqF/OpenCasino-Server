package com.opencasino.server.user

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

@SpringBootTest(
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///ledgertest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:ledgertest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
    ]
)
@ActiveProfiles("test")
class BalanceLedgerServiceIntegrationTest {

    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var ledger: BalanceLedgerRepository
    @Autowired lateinit var ledgerService: BalanceLedgerService
    @Autowired lateinit var template: R2dbcEntityTemplate

    private fun newUser(initialBalance: Double = 100.0): User {
        val user = User(
            email = "ledger-${UUID.randomUUID()}@example.com",
            displayName = "ledger-user",
            balance = initialBalance,
        )
        users.save(user).block()
        return user
    }

    @Test
    fun `applyDelta inserts a ledger row and updates user balance atomically`() {
        val user = newUser(100.0)
        val roundId = UUID.randomUUID()

        StepVerifier.create(
            ledgerService.applyDelta(user.id, roundId, 25.0, BalanceLedgerReason.BLACKJACK_ROUND)
        )
            .assertNext { entry ->
                assertEquals(user.id, entry.userId)
                assertEquals(roundId, entry.roundId)
                assertEquals(25.0, entry.delta)
                assertEquals(BalanceLedgerReason.BLACKJACK_ROUND, entry.reason)
            }
            .verifyComplete()

        assertEquals(125.0, users.findById(user.id).block()!!.balance)
        val rows = ledger.findByUserId(user.id).collectList().block()!!
        assertEquals(1, rows.size)
    }

    @Test
    fun `multiple deltas accumulate in user balance and all rows persist`() {
        val user = newUser(50.0)
        val r1 = UUID.randomUUID()
        val r2 = UUID.randomUUID()
        val r3 = UUID.randomUUID()

        ledgerService.applyDelta(user.id, r1, -10.0, BalanceLedgerReason.BLACKJACK_ROUND).block()
        ledgerService.applyDelta(user.id, r2, 30.0, BalanceLedgerReason.BLACKJACK_ROUND).block()
        ledgerService.applyDelta(user.id, r3, -5.0, BalanceLedgerReason.POKER_BUY_IN).block()

        assertEquals(65.0, users.findById(user.id).block()!!.balance)
        val rows = ledger.findByUserId(user.id).collectList().block()!!
        assertEquals(3, rows.size)
        assertEquals(15.0, rows.sumOf { it.delta }, 1e-9)
    }

    @Test
    fun `applyDelta with zero delta inserts ledger row but does not change balance`() {
        val user = newUser(200.0)

        ledgerService.applyDelta(user.id, UUID.randomUUID(), 0.0, BalanceLedgerReason.ADJUSTMENT).block()

        assertEquals(200.0, users.findById(user.id).block()!!.balance)
        assertEquals(1, ledger.findByUserId(user.id).collectList().block()!!.size)
    }

    @Test
    fun `findByRoundId returns rows for that round only`() {
        val user = newUser(0.0)
        val targetRound = UUID.randomUUID()
        val otherRound = UUID.randomUUID()

        ledgerService.applyDelta(user.id, targetRound, 10.0, BalanceLedgerReason.BLACKJACK_ROUND).block()
        ledgerService.applyDelta(user.id, targetRound, 5.0, BalanceLedgerReason.BLACKJACK_ROUND).block()
        ledgerService.applyDelta(user.id, otherRound, 99.0, BalanceLedgerReason.BLACKJACK_ROUND).block()

        StepVerifier.create(ledger.findByRoundId(targetRound).collectList())
            .assertNext { list ->
                assertEquals(2, list.size)
                assertTrue(list.all { it.roundId == targetRound })
            }
            .verifyComplete()
    }
}