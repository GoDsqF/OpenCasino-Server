package com.opencasino.server.network.pack

import com.opencasino.server.game.blackjack.model.BlackjackCondition
import com.opencasino.server.game.model.Card
import com.opencasino.server.game.model.Rank
import com.opencasino.server.game.model.Suit
import com.opencasino.server.network.pack.blackjack.info.InfoPack
import com.opencasino.server.network.pack.blackjack.info.PlayerInfoPack
import com.opencasino.server.network.pack.blackjack.shared.BlackjackConditionPack
import com.opencasino.server.network.pack.blackjack.shared.GameSettingsPack
import com.opencasino.server.network.pack.blackjack.shared.RoomPack
import com.opencasino.server.network.pack.blackjack.update.GameUpdatePack
import com.opencasino.server.network.pack.blackjack.update.PrivatePlayerUpdatePack
import com.opencasino.server.network.pack.blackjack.update.PublicPlayerUpdatePack
import com.opencasino.server.network.pack.poker.info.PlayerInfoPack as PokerPlayerInfoPack
import com.opencasino.server.network.pack.poker.info.InfoPack as PokerInfoPack
import com.opencasino.server.network.pack.poker.shared.PokerConditionPack
import com.opencasino.server.network.pack.poker.shared.GameSettingsPack as PokerGameSettingsPack
import com.opencasino.server.network.pack.poker.shared.RoomPack as PokerRoomPack
import com.opencasino.server.network.pack.poker.update.PrivatePlayerUpdatePack as PokerPrivatePlayerUpdatePack
import com.opencasino.server.network.pack.poker.update.PublicPlayerUpdatePack as PokerPublicPlayerUpdatePack
import com.opencasino.server.network.pack.shared.DealerUpdatePack
import com.opencasino.server.network.pack.shared.ExceptionPack
import com.opencasino.server.network.pack.shared.GameMessagePack
import com.opencasino.server.network.pack.update.PlayerHandUpdatePack
import com.opencasino.server.service.shared.BlackjackDecision
import com.opencasino.server.service.shared.PokerDecision
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PacksTest {

    // =========================================================================
    // Blackjack Packs
    // =========================================================================

    @Nested
    inner class BlackjackPacks {

        @Test
        fun `PlayerInfoPack stores id and balance`() {
            val pack = PlayerInfoPack(42L, 1500.0)
            assertEquals(42L, pack.id)
            assertEquals(1500.0, pack.balance)
        }

        @Test
        fun `InfoPack stores player info and metadata`() {
            val playerInfo = PlayerInfoPack(1L, 100.0)
            val pack = InfoPack(playerInfo, 300L, 5L)
            assertEquals(playerInfo, pack.player)
            assertEquals(300L, pack.loopRate)
            assertEquals(5L, pack.playersCount)
        }

        @Test
        fun `PrivatePlayerUpdatePack stores all fields`() {
            val pack = PrivatePlayerUpdatePack(1L, 500.0, 50.0, BlackjackDecision.HIT, listOf("HIT", "STAND"))
            assertEquals(1L, pack.id)
            assertEquals(500.0, pack.balance)
            assertEquals(50.0, pack.currentBet)
            assertEquals(BlackjackDecision.HIT, pack.lastDecision)
            assertEquals(listOf("HIT", "STAND"), pack.availableActions)
        }

        @Test
        fun `GameSettingsPack stores roomId and loop rate`() {
            val pack = GameSettingsPack("room-id-123", 300L)
            assertEquals("room-id-123", pack.roomId)
            assertEquals(300L, pack.loopRate)
        }

        @Test
        fun `RoomPack stores timestamp and roomId`() {
            val pack = RoomPack(1234567890L, "room-uuid")
            assertEquals(1234567890L, pack.timestamp)
            assertEquals("room-uuid", pack.roomId)
        }

        @Test
        fun `BlackjackConditionPack stores condition string`() {
            val pack = BlackjackConditionPack(BlackjackCondition.PlayerWin.toString())
            assertEquals("PlayerWin", pack.condition)
        }

        @Test
        fun `GameUpdatePack assembles correctly`() {
            val privateUpdate = PrivatePlayerUpdatePack(1L, 100.0, 25.0, BlackjackDecision.STAND, emptyList())
            val publicUpdate = PublicPlayerUpdatePack(1L, BlackjackDecision.STAND)
            val card = Card(Rank.CA, Suit.SPADES)
            val handUpdate = PlayerHandUpdatePack(publicUpdate, listOf(card))
            val dealerUpdate = DealerUpdatePack(listOf(card, null))

            val pack = GameUpdatePack(privateUpdate, listOf(handUpdate), dealerUpdate)

            assertEquals(privateUpdate, pack.player)
            assertEquals(1, pack.players.size)
            assertEquals(2, pack.dealer.cards.size)
            assertNull(pack.dealer.cards[1])
        }

        @Test
        fun `PublicPlayerUpdatePack stores only id and lastDecision`() {
            val pack = PublicPlayerUpdatePack(7L, BlackjackDecision.HIT)
            assertEquals(7L, pack.id)
            assertEquals(BlackjackDecision.HIT, pack.lastDecision)
        }
    }

    // =========================================================================
    // Poker Packs
    // =========================================================================

    @Nested
    inner class PokerPacks {

        @Test
        fun `PokerPlayerInfoPack stores id and balance`() {
            val pack = PokerPlayerInfoPack(99L, 2000.0)
            assertEquals(99L, pack.id)
            assertEquals(2000.0, pack.balance)
        }

        @Test
        fun `PokerInfoPack stores player info and metadata`() {
            val playerInfo = PokerPlayerInfoPack(1L, 500.0)
            val pack = PokerInfoPack(playerInfo, 200L, 3L)
            assertEquals(playerInfo, pack.player)
            assertEquals(200L, pack.loopRate)
            assertEquals(3L, pack.playersCount)
        }

        @Test
        fun `PokerPrivatePlayerUpdatePack stores all fields`() {
            val pack = PokerPrivatePlayerUpdatePack(
                5L, 2, 1000.0, 200.0, PokerDecision.RAISE, listOf("FOLD", "ALL_IN", "CALL", "RAISE")
            )
            assertEquals(5L, pack.id)
            assertEquals(2, pack.position)
            assertEquals(1000.0, pack.stack)
            assertEquals(200.0, pack.currentBet)
            assertEquals(PokerDecision.RAISE, pack.lastDecision)
            assertEquals(listOf("FOLD", "ALL_IN", "CALL", "RAISE"), pack.availableActions)
        }

        @Test
        fun `PokerGameSettingsPack stores roomId and loop rate`() {
            val pack = PokerGameSettingsPack("poker-id-456", 150L)
            assertEquals("poker-id-456", pack.roomId)
            assertEquals(150L, pack.loopRate)
        }

        @Test
        fun `PokerRoomPack stores timestamp and roomId`() {
            val pack = PokerRoomPack(9876543210L, "poker-room-uuid")
            assertEquals(9876543210L, pack.timestamp)
            assertEquals("poker-room-uuid", pack.roomId)
        }

        @Test
        fun `PokerConditionPack stores condition and position`() {
            val pack = PokerConditionPack("Win", 3)
            assertEquals("Win", pack.condition)
            assertEquals(3, pack.position)
        }
    }

    // =========================================================================
    // Shared Packs
    // =========================================================================

    @Nested
    inner class SharedPacks {

        @Test
        fun `DealerUpdatePack stores cards with nulls`() {
            val visible = Card(Rank.CK, Suit.HEARTS)
            val pack = DealerUpdatePack(listOf(visible, null))
            assertEquals(2, pack.cards.size)
            assertNotNull(pack.cards[0])
            assertNull(pack.cards[1])
        }

        @Test
        fun `DealerUpdatePack empty cards`() {
            val pack = DealerUpdatePack(emptyList())
            assertTrue(pack.cards.isEmpty())
        }

        @Test
        fun `ExceptionPack stores message`() {
            val pack = ExceptionPack("Something went wrong")
            assertEquals("Something went wrong", pack.message)
        }

        @Test
        fun `GameMessagePack stores type and message`() {
            val pack = GameMessagePack(1, "System message")
            assertEquals(1, pack.messageType)
            assertEquals("System message", pack.message)
        }

        @Test
        fun `PlayerHandUpdatePack stores player and cards`() {
            val publicUpdate = PublicPlayerUpdatePack(1L, BlackjackDecision.HIT)
            val cards = listOf(
                Card(Rank.CA, Suit.SPADES),
                Card(Rank.CK, Suit.HEARTS)
            )
            val pack = PlayerHandUpdatePack(publicUpdate, cards)
            assertEquals(publicUpdate, pack.player)
            assertEquals(2, pack.cards.size)
        }

        @Test
        fun `PlayerHandUpdatePack with hidden cards (nulls)`() {
            val publicUpdate = PokerPublicPlayerUpdatePack(2L, 1, PokerDecision.CHECK)
            val cards: List<Card?> = listOf(null, null)
            val pack = PlayerHandUpdatePack(publicUpdate, cards)
            assertEquals(2, pack.cards.size)
            assertNull(pack.cards[0])
            assertNull(pack.cards[1])
        }
    }

    // =========================================================================
    // Интерфейсы Pack
    // =========================================================================

    @Nested
    inner class PackInterfaces {

        @Test
        fun `PlayerInfoPack implements InitPack`() {
            val pack: InitPack = PlayerInfoPack(1L, 100.0)
            assertTrue(pack is Pack)
        }

        @Test
        fun `PrivatePlayerUpdatePack implements PrivateUpdatePack`() {
            val pack: PrivateUpdatePack = PrivatePlayerUpdatePack(1L, 100.0, 0.0, BlackjackDecision.NONE, emptyList())
            assertTrue(pack is UpdatePack)
            assertTrue(pack is Pack)
        }

        @Test
        fun `ExceptionPack implements Pack`() {
            val pack: Pack = ExceptionPack("error")
            assertNotNull(pack)
        }

        @Test
        fun `GameMessagePack implements InitPack`() {
            val pack: InitPack = GameMessagePack(1, "msg")
            assertTrue(pack is Pack)
        }

        @Test
        fun `GameSettingsPack implements InitPack`() {
            val pack: InitPack = GameSettingsPack("room-id", 300L)
            assertTrue(pack is Pack)
        }

        @Test
        fun `RoomPack implements InitPack`() {
            val pack: InitPack = RoomPack(0L, "id")
            assertTrue(pack is Pack)
        }
    }
}