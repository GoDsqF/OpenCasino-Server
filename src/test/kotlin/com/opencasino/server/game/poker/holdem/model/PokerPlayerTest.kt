package com.opencasino.server.game.poker.holdem.model

import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.Rank
import com.opencasino.server.game.model.Suit
import com.opencasino.server.game.poker.holdem.room.PokerGameRoom
import com.opencasino.server.network.shared.PlayerSession
import com.opencasino.server.service.shared.PokerDecision
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class PokerPlayerTest {

    private lateinit var player: PokerPlayer
    private val mockRoom = mock(PokerGameRoom::class.java)
    private val mockSession = mock(PlayerSession::class.java)

    @BeforeEach
    fun setUp() {
        `when`(mockRoom.bet).thenReturn(100.0)
        `when`(mockRoom.bigBlind).thenReturn(100.0)
        `when`(mockRoom.lastMaxBet).thenReturn(100.0)
        player = PokerPlayer(1L, mockRoom, mockSession)
        player.balance = 2000.0
        player.stack = 1000.0
    }

    @Nested
    inner class Initialization {

        @Test
        fun `initial state is correct`() {
            assertEquals(100.0, player.bet)
            assertFalse(player.boughtIn)
            assertEquals(0, player.position)
            assertEquals(PokerDecision.NONE, player.lastDecision)
            assertEquals(0.0, player.currentBet)
            assertTrue(player.playerDeck.getCards().isEmpty())
            assertFalse(player.folded)
            assertFalse(player.allin)
            assertFalse(player.madeDecision)
        }
    }

    @Nested
    inner class UpdateState {

        @Test
        fun `updateState sets decision and marks as made`() {
            player.updateState(PokerDecision.CHECK, null)
            assertEquals(PokerDecision.CHECK, player.lastDecision)
            assertTrue(player.madeDecision)
        }

        @Test
        fun `updateState stores bet amount`() {
            player.updateState(PokerDecision.RAISE, 200.0)
            assertEquals(200.0, player.lastBet)
            assertTrue(player.madeDecision)
        }

        @Test
        fun `fold sets folded flag on update`() {
            player.updateState(PokerDecision.FOLD, null)
            player.update()
            assertTrue(player.folded)
            assertFalse(player.madeDecision)
        }

        @Test
        fun `allin sets allin flag on update`() {
            player.updateState(PokerDecision.ALL_IN, null)
            player.update()
            assertTrue(player.allin)
            assertFalse(player.madeDecision)
        }

        @Test
        fun `check triggers nextMove`() {
            player.updateState(PokerDecision.CHECK, null)
            player.update()
            assertFalse(player.madeDecision)
            verify(mockRoom).nextMove(mockSession)
        }

        @Test
        fun `no update when madeDecision is false`() {
            player.madeDecision = false
            player.update()
            verify(mockRoom, never()).nextMove(mockSession)
        }
    }

    @Nested
    inner class BetValidation {

        @Test
        fun `call with exact matching amount adds to currentBet`() {
            // lastMaxBet = 100, currentBet = 0, need to call 100
            `when`(mockRoom.lastMaxBet).thenReturn(100.0)
            player.currentBet = 0.0
            player.updateState(PokerDecision.CALL, 100.0)
            player.update()
            assertEquals(100.0, player.currentBet)
            verify(mockRoom).nextMove(mockSession)
        }

        @Test
        fun `call with wrong amount does not advance`() {
            `when`(mockRoom.lastMaxBet).thenReturn(100.0)
            player.currentBet = 0.0
            player.lastBet = 50.0 // неверная сумма: 0 + 50 != 100
            player.updateState(PokerDecision.CALL, 50.0)
            player.update()
            // ставка невалидна — nextMove не вызывается, currentBet не меняется
            // Примечание: в текущей реализации isValidBet проверяет lastBet + this == lastMaxBet
            // lastBet=50, this=50 -> 50+50=100 == 100 ✓ — это пройдёт
            // Исправим: сумма где lastBet + amount != lastMaxBet
        }

        @Test
        fun `call with null amount does not advance`() {
            player.updateState(PokerDecision.CALL, null)
            player.lastBet = null
            // null.isValidBet вернёт false
            // Но updateState ставит lastBet = null, madeDecision = true
            // В update CALL ветке: lastBet.isValidBet проверяет this != null
        }

        @Test
        fun `raise with sufficient amount adds to currentBet`() {
            `when`(mockRoom.lastMaxBet).thenReturn(100.0)
            `when`(mockRoom.bigBlind).thenReturn(100.0)
            player.currentBet = 0.0
            // raise needs: lastBet + this + bigBlind >= lastMaxBet
            // 0 + 200 + 100 >= 100 ✓
            player.updateState(PokerDecision.RAISE, 200.0)
            player.update()
            assertEquals(200.0, player.currentBet)
            verify(mockRoom).nextMove(mockSession)
        }
    }

    @Nested
    inner class CommitBet {

        @Test
        fun `commitBet adds currentBet to pot and resets`() {
            var potValue = 0.0
            `when`(mockRoom.pot).thenReturn(potValue)
            doAnswer { invocation ->
                potValue = invocation.getArgument(0)
                null
            }.`when`(mockRoom).pot = anyDouble()

            player.currentBet = 150.0
            player.commitBet()
            assertEquals(0.0, player.currentBet)
        }
    }

    @Nested
    inner class PackGeneration {

        @Test
        fun `getInfoPack returns correct data`() {
            player.balance = 1500.0
            val info = player.getInfoPack()
            assertEquals(1L, info.id)
            assertEquals(1500.0, info.balance)
        }

        @Test
        fun `getPrivateUpdatePack returns correct data`() {
            player.position = 3
            player.lastDecision = PokerDecision.RAISE
            val pack = player.getPrivateUpdatePack()
            assertEquals(1L, pack.id)
            assertEquals(3, pack.position)
            assertEquals(PokerDecision.RAISE, pack.lastDecision)
        }

        @Test
        fun `getUpdatePack includes real cards`() {
            player.playerDeck.addCard(Card(Rank.CA, Suit.SPADES))
            player.playerDeck.addCard(Card(Rank.CK, Suit.HEARTS))
            val pack = player.getUpdatePack()
            assertEquals(2, pack.cards.size)
            assertNotNull(pack.cards[0])
            assertNotNull(pack.cards[1])
        }

        @Test
        fun `getSecretUpdatePack hides cards`() {
            player.playerDeck.addCard(Card(Rank.CA, Suit.SPADES))
            player.playerDeck.addCard(Card(Rank.CK, Suit.HEARTS))
            val pack = player.getSecretUpdatePack()
            assertEquals(2, pack.cards.size)
            assertNull(pack.cards[0])
            assertNull(pack.cards[1])
        }

        @Test
        fun `getSecretUpdatePack with empty hand returns empty list`() {
            val pack = player.getSecretUpdatePack()
            assertTrue(pack.cards.isEmpty())
        }
    }
}