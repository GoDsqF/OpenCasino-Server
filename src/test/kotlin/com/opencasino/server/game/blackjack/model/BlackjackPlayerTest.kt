package com.opencasino.server.game.blackjack.model

import com.opencasino.server.config.MIN_BLACKJACK_BET
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.model.Rank
import com.opencasino.server.game.model.Suit
import com.opencasino.server.network.pack.blackjack.info.PlayerInfoPack
import com.opencasino.server.network.pack.blackjack.update.PrivatePlayerUpdatePack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.shared.BlackjackDecision
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class BlackjackPlayerTest {

    private lateinit var player: BlackjackPlayer
    private val mockRoom = mock(BlackjackGameRoom::class.java)
    private val mockSession = mock(PlayerSession::class.java)
    private val mockDeck = mock(CardDeck::class.java)

    @BeforeEach
    fun setUp() {
        player = BlackjackPlayer(1L, mockRoom, mockSession)
        player.balance = 1000.0
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    @Nested
    inner class Initialization {

        @Test
        fun `initial bet is MIN_BLACKJACK_BET`() {
            assertEquals(MIN_BLACKJACK_BET, player.bet)
        }

        @Test
        fun `initial position is 0`() {
            assertEquals(0, player.position)
        }

        @Test
        fun `initial lastDecision is NONE`() {
            assertEquals(BlackjackDecision.NONE, player.lastDecision)
        }

        @Test
        fun `initial playerDeck is empty`() {
            assertTrue(player.playerDeck.getCards().isEmpty())
        }

        @Test
        fun `initial madeDecision is false`() {
            assertFalse(player.madeDecision)
        }

        @Test
        fun `initial isAlive is true`() {
            assertTrue(player.isAlive)
        }

        @Test
        fun `id is stored correctly`() {
            assertEquals(1L, player.id)
        }

        @Test
        fun `userSession is stored correctly`() {
            assertEquals(mockSession, player.userSession)
        }
    }

    // =========================================================================
    // UpdateState
    // =========================================================================

    @Nested
    inner class UpdateState {

        @Test
        fun `updateState sets lastDecision to HIT`() {
            player.updateState(BlackjackDecision.HIT)
            assertEquals(BlackjackDecision.HIT, player.lastDecision)
        }

        @Test
        fun `updateState sets lastDecision to STAND`() {
            player.updateState(BlackjackDecision.STAND)
            assertEquals(BlackjackDecision.STAND, player.lastDecision)
        }

        @Test
        fun `updateState sets madeDecision to true`() {
            player.updateState(BlackjackDecision.HIT)
            assertTrue(player.madeDecision)
        }

        @Test
        fun `updateState can be called multiple times`() {
            player.updateState(BlackjackDecision.HIT)
            assertEquals(BlackjackDecision.HIT, player.lastDecision)

            player.updateState(BlackjackDecision.STAND)
            assertEquals(BlackjackDecision.STAND, player.lastDecision)
            assertTrue(player.madeDecision)
        }

        @Test
        fun `updateState with DOUBLE sets decision`() {
            player.updateState(BlackjackDecision.DOUBLE)
            assertEquals(BlackjackDecision.DOUBLE, player.lastDecision)
            assertTrue(player.madeDecision)
        }

        @Test
        fun `updateState with SPLIT sets decision`() {
            player.updateState(BlackjackDecision.SPLIT)
            assertEquals(BlackjackDecision.SPLIT, player.lastDecision)
            assertTrue(player.madeDecision)
        }

        @Test
        fun `updateState with NONE sets decision`() {
            player.updateState(BlackjackDecision.NONE)
            assertEquals(BlackjackDecision.NONE, player.lastDecision)
            assertTrue(player.madeDecision)
        }
    }

    // =========================================================================
    // Update (game logic)
    // =========================================================================

    @Nested
    inner class Update {

        @Test
        fun `update does nothing when madeDecision is false`() {
            player.madeDecision = false
            player.update()
            // No interactions with room
            verify(mockRoom, never()).onDealerTurn()
            verify(mockRoom, never()).onPlayerTurn()
        }

        @Test
        fun `update with STAND calls onDealerTurn and resets madeDecision`() {
            player.updateState(BlackjackDecision.STAND)
            player.update()
            assertFalse(player.madeDecision)
            verify(mockRoom).onDealerTurn()
        }

        @Test
        fun `update with HIT deals card and calls onPlayerTurn`() {
            val gameDeck = CardDeck(1)
            `when`(mockRoom.deck).thenReturn(gameDeck)

            player.updateState(BlackjackDecision.HIT)
            player.update()

            assertFalse(player.madeDecision)
            assertEquals(1, player.playerDeck.getCards().size)
            verify(mockRoom).onPlayerTurn()
        }

        @Test
        fun `update with HIT multiple times accumulates cards`() {
            val gameDeck = CardDeck(1)
            `when`(mockRoom.deck).thenReturn(gameDeck)

            player.updateState(BlackjackDecision.HIT)
            player.update()

            player.updateState(BlackjackDecision.HIT)
            player.update()

            assertEquals(2, player.playerDeck.getCards().size)
            verify(mockRoom, times(2)).onPlayerTurn()
        }

        @Test
        fun `update with DOUBLE throws TODO`() {
            player.updateState(BlackjackDecision.DOUBLE)
            assertThrows(NotImplementedError::class.java) {
                player.update()
            }
        }

        @Test
        fun `update with SPLIT throws TODO`() {
            player.updateState(BlackjackDecision.SPLIT)
            assertThrows(NotImplementedError::class.java) {
                player.update()
            }
        }

        @Test
        fun `update with NONE throws TODO`() {
            player.updateState(BlackjackDecision.NONE)
            assertThrows(NotImplementedError::class.java) {
                player.update()
            }
        }

        @Test
        fun `STAND does not deal cards`() {
            player.updateState(BlackjackDecision.STAND)
            player.update()
            assertTrue(player.playerDeck.getCards().isEmpty())
        }
    }

    // =========================================================================
    // Info Pack
    // =========================================================================

    @Nested
    inner class InfoPackGeneration {

        @Test
        fun `info returns PlayerInfoPack with correct id`() {
            val info = player.info()
            assertEquals(1L, info.id)
        }

        @Test
        fun `info returns PlayerInfoPack with correct balance`() {
            player.balance = 2500.0
            val info = player.info()
            assertEquals(2500.0, info.balance)
        }

        @Test
        fun `getInfoPack returns same as info`() {
            player.balance = 500.0
            val info = player.info()
            val infoPack = player.getInfoPack()
            assertEquals(info.id, infoPack.id)
            assertEquals(info.balance, infoPack.balance)
        }

        @Test
        fun `info with zero balance`() {
            player.balance = 0.0
            val info = player.info()
            assertEquals(0.0, info.balance)
        }

        @Test
        fun `info with negative balance`() {
            player.balance = -100.0
            val info = player.info()
            assertEquals(-100.0, info.balance)
        }

        @Test
        fun `info returns InitPack type`() {
            val info = player.info()
            assertTrue(info is PlayerInfoPack)
        }
    }

    // =========================================================================
    // Update Pack
    // =========================================================================

    @Nested
    inner class UpdatePackGeneration {

        @Test
        fun `getUpdatePack returns pack with empty cards when no cards dealt`() {
            val pack = player.getUpdatePack()
            assertTrue(pack.cards.isEmpty())
        }

        @Test
        fun `getUpdatePack includes cards from playerDeck`() {
            player.playerDeck.addCard(Card(Rank.CA, Suit.SPADES))
            player.playerDeck.addCard(Card(Rank.CK, Suit.HEARTS))

            val pack = player.getUpdatePack()
            assertEquals(2, pack.cards.size)
            assertNotNull(pack.cards[0])
            assertNotNull(pack.cards[1])
        }

        @Test
        fun `getUpdatePack card ranks are correct`() {
            player.playerDeck.addCard(Card(Rank.C7, Suit.DIAMONDS))
            player.playerDeck.addCard(Card(Rank.CQ, Suit.CLUBS))

            val pack = player.getUpdatePack()
            assertEquals(Rank.C7, pack.cards[0]!!.rank)
            assertEquals(Rank.CQ, pack.cards[1]!!.rank)
        }

        @Test
        fun `getUpdatePack card suits are correct`() {
            player.playerDeck.addCard(Card(Rank.C7, Suit.DIAMONDS))
            player.playerDeck.addCard(Card(Rank.CQ, Suit.CLUBS))

            val pack = player.getUpdatePack()
            assertEquals(Suit.DIAMONDS, pack.cards[0]!!.suit)
            assertEquals(Suit.CLUBS, pack.cards[1]!!.suit)
        }

        @Test
        fun `getUpdatePack contains privateUpdatePack`() {
            player.balance = 750.0
            player.updateState(BlackjackDecision.HIT)

            val pack = player.getUpdatePack()
            val privatePack = pack.player as PrivatePlayerUpdatePack
            assertEquals(1L, privatePack.id)
            assertEquals(750.0, privatePack.balance)
            assertEquals(BlackjackDecision.HIT, privatePack.lastDecision)
        }

        @Test
        fun `getUpdatePack is PlayerHandUpdatePack type`() {
            val pack = player.getUpdatePack()
            assertTrue(pack is PlayerHandUpdatePack)
        }
    }

    // =========================================================================
    // Private Update Pack
    // =========================================================================

    @Nested
    inner class PrivateUpdatePackGeneration {

        @Test
        fun `getPrivateUpdatePack returns correct id`() {
            val pack = player.getPrivateUpdatePack()
            assertEquals(1L, pack.id)
        }

        @Test
        fun `getPrivateUpdatePack returns correct balance`() {
            player.balance = 3000.0
            val pack = player.getPrivateUpdatePack()
            assertEquals(3000.0, pack.balance)
        }

        @Test
        fun `getPrivateUpdatePack returns correct lastDecision`() {
            player.updateState(BlackjackDecision.STAND)
            val pack = player.getPrivateUpdatePack()
            assertEquals(BlackjackDecision.STAND, pack.lastDecision)
        }

        @Test
        fun `getPrivateUpdatePack reflects initial NONE decision`() {
            val pack = player.getPrivateUpdatePack()
            assertEquals(BlackjackDecision.NONE, pack.lastDecision)
        }

        @Test
        fun `getPrivateUpdatePack updates after decision change`() {
            player.updateState(BlackjackDecision.HIT)
            val pack1 = player.getPrivateUpdatePack()
            assertEquals(BlackjackDecision.HIT, pack1.lastDecision)

            player.updateState(BlackjackDecision.STAND)
            val pack2 = player.getPrivateUpdatePack()
            assertEquals(BlackjackDecision.STAND, pack2.lastDecision)
        }

        @Test
        fun `getPrivateUpdatePack is PrivatePlayerUpdatePack type`() {
            val pack = player.getPrivateUpdatePack()
            assertTrue(pack is PrivatePlayerUpdatePack)
        }
    }

    // =========================================================================
    // Balance and Bet
    // =========================================================================

    @Nested
    inner class BalanceAndBet {

        @Test
        fun `balance can be set and retrieved`() {
            player.balance = 5000.0
            assertEquals(5000.0, player.balance)
        }

        @Test
        fun `bet defaults to MIN_BLACKJACK_BET`() {
            assertEquals(MIN_BLACKJACK_BET, player.bet)
        }

        @Test
        fun `bet can be changed`() {
            player.bet = 100.0
            assertEquals(100.0, player.bet)
        }

        @Test
        fun `balance reflects in info pack`() {
            player.balance = 999.0
            assertEquals(999.0, player.info().balance)
        }

        @Test
        fun `balance reflects in private update pack`() {
            player.balance = 888.0
            assertEquals(888.0, player.getPrivateUpdatePack().balance)
        }
    }

    // =========================================================================
    // Player State
    // =========================================================================

    @Nested
    inner class PlayerState {

        @Test
        fun `isAlive can be toggled`() {
            assertTrue(player.isAlive)
            player.isAlive = false
            assertFalse(player.isAlive)
        }

        @Test
        fun `position can be changed`() {
            player.position = 3
            assertEquals(3, player.position)
        }

        @Test
        fun `playerDeck can be cleared`() {
            player.playerDeck.addCard(Card(Rank.CA, Suit.SPADES))
            player.playerDeck.addCard(Card(Rank.CK, Suit.HEARTS))
            assertEquals(2, player.playerDeck.getCards().size)

            player.playerDeck.clear()
            assertTrue(player.playerDeck.getCards().isEmpty())
        }

        @Test
        fun `madeDecision resets after STAND update`() {
            player.updateState(BlackjackDecision.STAND)
            assertTrue(player.madeDecision)
            player.update()
            assertFalse(player.madeDecision)
        }

        @Test
        fun `madeDecision resets after HIT update`() {
            val gameDeck = CardDeck(1)
            `when`(mockRoom.deck).thenReturn(gameDeck)

            player.updateState(BlackjackDecision.HIT)
            assertTrue(player.madeDecision)
            player.update()
            assertFalse(player.madeDecision)
        }
    }

    // =========================================================================
    // Entity equality (inherited from AbstractEntity)
    // =========================================================================

    @Nested
    inner class EntityEquality {

        @Test
        fun `players with same id are equal`() {
            val player1 = BlackjackPlayer(42L, mockRoom, mockSession)
            val player2 = BlackjackPlayer(42L, mockRoom, mockSession)
            assertEquals(player1, player2)
        }

        @Test
        fun `players with different ids are not equal`() {
            val player1 = BlackjackPlayer(1L, mockRoom, mockSession)
            val player2 = BlackjackPlayer(2L, mockRoom, mockSession)
            assertNotEquals(player1, player2)
        }

        @Test
        fun `player equals itself`() {
            assertEquals(player, player)
        }

        @Test
        fun `player not equals null`() {
            assertNotEquals(null, player)
        }

        @Test
        fun `same id produces same hashCode`() {
            val player1 = BlackjackPlayer(42L, mockRoom, mockSession)
            val player2 = BlackjackPlayer(42L, mockRoom, mockSession)
            assertEquals(player1.hashCode(), player2.hashCode())
        }

        @Test
        fun `different ids produce different hashCodes`() {
            val player1 = BlackjackPlayer(1L, mockRoom, mockSession)
            val player2 = BlackjackPlayer(2L, mockRoom, mockSession)
            assertNotEquals(player1.hashCode(), player2.hashCode())
        }
    }

    // =========================================================================
    // Full game flow simulation
    // =========================================================================

    @Nested
    inner class GameFlowSimulation {

        @Test
        fun `player hits twice then stands - full flow`() {
            val gameDeck = CardDeck(1)
            `when`(mockRoom.deck).thenReturn(gameDeck)

            // Hit 1
            player.updateState(BlackjackDecision.HIT)
            player.update()
            assertEquals(1, player.playerDeck.getCards().size)
            assertFalse(player.madeDecision)

            // Hit 2
            player.updateState(BlackjackDecision.HIT)
            player.update()
            assertEquals(2, player.playerDeck.getCards().size)
            assertFalse(player.madeDecision)

            // Stand
            player.updateState(BlackjackDecision.STAND)
            player.update()
            assertEquals(2, player.playerDeck.getCards().size) // no new cards
            assertFalse(player.madeDecision)

            verify(mockRoom, times(2)).onPlayerTurn()
            verify(mockRoom, times(1)).onDealerTurn()
        }

        @Test
        fun `player stands immediately - no cards dealt`() {
            player.updateState(BlackjackDecision.STAND)
            player.update()

            assertTrue(player.playerDeck.getCards().isEmpty())
            verify(mockRoom).onDealerTurn()
            verify(mockRoom, never()).onPlayerTurn()
        }

        @Test
        fun `decision reflected in packs after update`() {
            val gameDeck = CardDeck(1)
            `when`(mockRoom.deck).thenReturn(gameDeck)

            player.updateState(BlackjackDecision.HIT)
            val packBeforeUpdate = player.getPrivateUpdatePack()
            assertEquals(BlackjackDecision.HIT, packBeforeUpdate.lastDecision)

            player.update()

            val packAfterUpdate = player.getPrivateUpdatePack()
            assertEquals(BlackjackDecision.HIT, packAfterUpdate.lastDecision)
            assertEquals(1, player.getUpdatePack().cards.size)
        }

        @Test
        fun `reset player state for new round`() {
            val gameDeck = CardDeck(1)
            `when`(mockRoom.deck).thenReturn(gameDeck)

            // Play round
            player.updateState(BlackjackDecision.HIT)
            player.update()
            player.bet = 50.0

            // Simulate reset (as done in BlackjackGameRoom.reset())
            player.playerDeck.clear()
            player.bet = 0.0

            assertTrue(player.playerDeck.getCards().isEmpty())
            assertEquals(0.0, player.bet)
        }

        @Test
        fun `multiple updates without decision do nothing`() {
            player.update()
            player.update()
            player.update()

            verify(mockRoom, never()).onDealerTurn()
            verify(mockRoom, never()).onPlayerTurn()
            assertTrue(player.playerDeck.getCards().isEmpty())
        }
    }

    // =========================================================================
    // Different player instances
    // =========================================================================

    @Nested
    inner class MultipleInstances {

        @Test
        fun `two players have independent decks`() {
            val player2 = BlackjackPlayer(2L, mockRoom, mockSession)
            player2.balance = 500.0

            player.playerDeck.addCard(Card(Rank.CA, Suit.SPADES))
            assertTrue(player2.playerDeck.getCards().isEmpty())
            assertEquals(1, player.playerDeck.getCards().size)
        }

        @Test
        fun `two players have independent decisions`() {
            val player2 = BlackjackPlayer(2L, mockRoom, mockSession)
            player2.balance = 500.0

            player.updateState(BlackjackDecision.HIT)
            player2.updateState(BlackjackDecision.STAND)

            assertEquals(BlackjackDecision.HIT, player.lastDecision)
            assertEquals(BlackjackDecision.STAND, player2.lastDecision)
        }

        @Test
        fun `two players have independent balances`() {
            val player2 = BlackjackPlayer(2L, mockRoom, mockSession)
            player2.balance = 500.0

            assertEquals(1000.0, player.balance)
            assertEquals(500.0, player2.balance)
        }

        @Test
        fun `two players have independent alive states`() {
            val player2 = BlackjackPlayer(2L, mockRoom, mockSession)
            player2.balance = 500.0

            player.isAlive = false
            assertTrue(player2.isAlive)
            assertFalse(player.isAlive)
        }
    }
}