package com.opencasino.server.game.blackjack.model

import com.opencasino.server.config.MIN_BLACKJACK_BET
import com.opencasino.server.game.blackjack.room.BlackjackGameRoom
import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.CardDeck
import com.opencasino.server.game.model.Rank
import com.opencasino.server.game.model.Suit
import com.opencasino.server.network.pack.blackjack.update.PrivatePlayerUpdatePack
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
    private val mockDeck = CardDeck(1)

    @BeforeEach
    fun setUp() {
        `when`(mockRoom.deck).thenReturn(mockDeck)
        player = BlackjackPlayer(1L, mockRoom, mockSession)
        player.balance = 1000.0
    }

    @Nested
    inner class Initialization {

        @Test
        fun `default bet is MIN_BLACKJACK_BET`() {
            assertEquals(MIN_BLACKJACK_BET, player.bet)
        }

        @Test
        fun `initial decision is NONE`() {
            assertEquals(BlackjackDecision.NONE, player.lastDecision)
        }

        @Test
        fun `initial position is 0`() {
            assertEquals(0, player.position)
        }

        @Test
        fun `initial deck is empty`() {
            assertTrue(player.playerDeck.getCards().isEmpty())
        }

        @Test
        fun `player starts alive`() {
            assertTrue(player.isAlive)
        }

        @Test
        fun `madeDecision starts false`() {
            assertFalse(player.madeDecision)
        }
    }

    @Nested
    inner class UpdateState {

        @Test
        fun `updateState sets decision and flags`() {
            player.updateState(BlackjackDecision.HIT)
            assertEquals(BlackjackDecision.HIT, player.lastDecision)
            assertTrue(player.madeDecision)
        }

        @Test
        fun `updateState overwrites previous decision`() {
            player.updateState(BlackjackDecision.HIT)
            player.updateState(BlackjackDecision.STAND)
            assertEquals(BlackjackDecision.STAND, player.lastDecision)
            assertTrue(player.madeDecision)
        }
    }

    @Nested
    inner class UpdateLogic {

        @Test
        fun `STAND triggers dealer turn`() {
            player.updateState(BlackjackDecision.STAND)
            player.update()
            verify(mockRoom).onDealerTurn()
            assertFalse(player.madeDecision)
        }

        @Test
        fun `HIT deals card and triggers player turn`() {
            player.updateState(BlackjackDecision.HIT)
            player.update()
            verify(mockRoom).onPlayerTurn()
            assertFalse(player.madeDecision)
            // deck.dealCard вызывается на mockRoom.deck
        }

        @Test
        fun `no action when madeDecision is false`() {
            player.madeDecision = false
            player.update()
            verify(mockRoom, never()).onDealerTurn()
            verify(mockRoom, never()).onPlayerTurn()
        }

        @Test
        fun `STAND resets madeDecision`() {
            player.updateState(BlackjackDecision.STAND)
            assertTrue(player.madeDecision)
            player.update()
            assertFalse(player.madeDecision)
        }

        @Test
        fun `HIT resets madeDecision`() {
            player.updateState(BlackjackDecision.HIT)
            assertTrue(player.madeDecision)
            player.update()
            assertFalse(player.madeDecision)
        }

        @Test
        fun `multiple HIT STAND cycle works`() {
            player.updateState(BlackjackDecision.HIT)
            player.update()
            verify(mockRoom, times(1)).onPlayerTurn()

            player.updateState(BlackjackDecision.HIT)
            player.update()
            verify(mockRoom, times(2)).onPlayerTurn()

            player.updateState(BlackjackDecision.STAND)
            player.update()
            verify(mockRoom, times(1)).onDealerTurn()
        }
    }

    @Nested
    inner class PackGeneration {

        @Test
        fun `info returns PlayerInfoPack with correct data`() {
            player.balance = 500.0
            val info = player.info()
            assertEquals(1L, info.id)
            assertEquals(500.0, info.balance)
        }

        @Test
        fun `getInfoPack matches info()`() {
            player.balance = 777.0
            assertEquals(player.info(), player.getInfoPack())
        }

        @Test
        fun `getPrivateUpdatePack contains correct fields`() {
            player.balance = 300.0
            player.updateState(BlackjackDecision.HIT)
            val pack = player.getPrivateUpdatePack()
            assertEquals(1L, pack.id)
            assertEquals(300.0, pack.balance)
            assertEquals(BlackjackDecision.HIT, pack.lastDecision)
        }

        @Test
        fun `getUpdatePack contains cards`() {
            player.playerDeck.addCard(Card(Rank.CA, Suit.SPADES))
            player.playerDeck.addCard(Card(Rank.CK, Suit.HEARTS))
            val pack = player.getUpdatePack()
            assertEquals(2, pack.cards.size)
        }

        @Test
        fun `getUpdatePack with empty hand has no cards`() {
            val pack = player.getUpdatePack()
            assertTrue(pack.cards.isEmpty())
        }

        @Test
        fun `getUpdatePack includes private update pack`() {
            player.balance = 250.0
            player.updateState(BlackjackDecision.STAND)
            val pack = player.getUpdatePack()
            assertEquals(1L, (pack.player as PrivatePlayerUpdatePack).id)
            assertEquals(250.0, pack.player.balance)
            assertEquals(BlackjackDecision.STAND, pack.player.lastDecision)
        }
    }
}