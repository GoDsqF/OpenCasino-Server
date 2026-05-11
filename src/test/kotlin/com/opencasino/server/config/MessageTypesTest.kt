package com.opencasino.server.config

import com.opencasino.server.service.shared.MessageType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MessageTypesTest {

    @Test
    fun `all message type codes are unique`() {
        val codes = mutableListOf<MessageType>()
        for (t in MessageType.entries) {
            codes.add(t)
        }
        assertEquals(codes.size, codes.toSet().size, "Duplicate message type codes found")
    }

    @Test
    fun `MESSAGE is 1`() {
        assertEquals(1, MESSAGE)
    }

    @Test
    fun `UPDATE is 100`() {
        assertEquals(100, UPDATE)
    }

    @Test
    fun `INFO is 101`() {
        assertEquals(101, INFO)
    }

    @Test
    fun `PLAYER_DECISION is 200`() {
        assertEquals(200, PLAYER_DECISION)
    }

    @Test
    fun `FAILURE is 40`() {
        assertEquals(40, FAILURE)
    }

    @Test
    fun `BET is 77`() {
        assertEquals(77, BET)
    }

    @Test
    fun `GAME_ROOM_JOIN is 10`() {
        assertEquals(10, GAME_ROOM_JOIN)
    }

    @Test
    fun `GAME_ROOM_START is 20`() {
        assertEquals(20, GAME_ROOM_START)
    }

    @Test
    fun `GAME_ROOM_CLOSE is 21`() {
        assertEquals(21, GAME_ROOM_CLOSE)
    }

    @Test
    fun `success and failure codes are different for room join`() {
        assertNotEquals(GAME_ROOM_JOIN_SUCCESS, GAME_ROOM_JOIN_FAILURE)
    }

    @Test
    fun `success and failure codes are different for menu join`() {
        assertNotEquals(MAIN_MENU_JOIN_SUCCESS, MAIN_MENU_JOIN_FAILURE)
    }

    @Test
    fun `success and failure codes are different for bet`() {
        assertNotEquals(BET, BET_FAILURE)
    }

    @Test
    fun `AUTH_EVENT and GAME_LIST_UPDATE have distinct codes`() {
        assertNotEquals(AUTH_EVENT, GAME_LIST_UPDATE)
    }
}