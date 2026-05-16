package com.opencasino.server.event

import com.opencasino.server.event.poker.GameRoomCreateEvent
import com.opencasino.server.event.poker.GameSettingsUpdateEvent
import com.opencasino.server.event.poker.PokerPlayerDecisionEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EventsTest {

    @Test
    fun `BetEvent stores bet amount`() {
        val event = BetEvent(50.0)
        assertEquals(50.0, event.bet)
    }

    @Test
    fun `BetEvent with zero bet`() {
        val event = BetEvent(0.0)
        assertEquals(0.0, event.bet)
    }

    @Test
    fun `GameRoomJoinEvent stores reconnectKey and playerUUID`() {
        val event = GameRoomJoinEvent("key-123", "uuid-456")
        assertEquals("key-123", event.reconnectKey)
        assertEquals("uuid-456", event.playerUUID)
    }

    @Test
    fun `GameRoomJoinEvent allows null reconnectKey`() {
        val event = GameRoomJoinEvent(null, "uuid-456")
        assertNull(event.reconnectKey)
    }

    @Test
    fun `BlackjackPlayerDecisionEvent stores inputId`() {
        val event = BlackjackPlayerDecisionEvent("HIT")
        assertEquals("HIT", event.inputId)
    }

    @Test
    fun `PokerPlayerDecisionEvent stores inputId and amount`() {
        val event = PokerPlayerDecisionEvent("RAISE", 200.0)
        assertEquals("RAISE", event.inputId)
        assertEquals(200.0, event.amount)
    }

    @Test
    fun `PokerPlayerDecisionEvent amount defaults to null`() {
        val event = PokerPlayerDecisionEvent("CHECK")
        assertEquals("CHECK", event.inputId)
        assertNull(event.amount)
    }

    @Test
    fun `GameSettingsUpdateEvent stores all fields`() {
        val event = GameSettingsUpdateEvent("NoLimit", 100.0, 50.0, 1000.0)
        assertEquals("NoLimit", event.betType)
        assertEquals(100.0, event.bet)
        assertEquals(50.0, event.minLimit)
        assertEquals(1000.0, event.maxLimit)
    }

    @Test
    fun `GameSettingsUpdateEvent allows null betType`() {
        val event = GameSettingsUpdateEvent(null, 100.0, null, null)
        assertNull(event.betType)
        assertNull(event.minLimit)
        assertNull(event.maxLimit)
    }

    @Test
    fun `GameRoomCreateEvent stores playerUUID and settings`() {
        val settings = GameSettingsUpdateEvent("PotLimit", 50.0, 25.0, 500.0)
        val event = GameRoomCreateEvent("player-uuid-1", settings)
        assertEquals("player-uuid-1", event.playerUUID)
        assertEquals(settings, event.settings)
        assertEquals("PotLimit", event.settings.betType)
        assertEquals(50.0, event.settings.bet)
    }

    @Test
    fun `PlayerDecisionEvent stores inputId`() {
        val event = PlayerDecisionEvent("STAND")
        assertEquals("STAND", event.inputId)
    }

    @Test
    fun `all events extend AbstractEvent`() {
        assertTrue(BetEvent(0.0) is AbstractEvent)
        assertTrue(GameRoomJoinEvent(null, "") is AbstractEvent)
        assertTrue(BlackjackPlayerDecisionEvent("") is AbstractEvent)
        assertTrue(PlayerDecisionEvent("") is AbstractEvent)
        assertTrue(PokerPlayerDecisionEvent("") is AbstractEvent)
        assertTrue(GameSettingsUpdateEvent(null, 0.0, null, null) is AbstractEvent)
        assertTrue(GameRoomCreateEvent("", GameSettingsUpdateEvent(null, 0.0, null, null)) is AbstractEvent)
    }

    @Test
    fun `all events implement Event interface`() {
        assertTrue(BetEvent(0.0) is Event)
        assertTrue(GameRoomJoinEvent(null, "") is Event)
    }
}