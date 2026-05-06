package com.opencasino.server.network.shared

import com.opencasino.server.config.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MessageTest {

    @Test
    fun `constructor with type only`() {
        val msg = Message(UPDATE)
        assertEquals(UPDATE, msg.type)
        assertNull(msg.data)
    }

    @Test
    fun `constructor with type and data`() {
        val data = mapOf("key" to "value")
        val msg = Message(MESSAGE, data)
        assertEquals(MESSAGE, msg.type)
        assertEquals(data, msg.data)
    }

    @Test
    fun `data can be any type`() {
        val msg1 = Message(UPDATE, "string data")
        assertEquals("string data", msg1.data)

        val msg2 = Message(UPDATE, 42)
        assertEquals(42, msg2.data)

        val msg3 = Message(UPDATE, listOf(1, 2, 3))
        assertEquals(listOf(1, 2, 3), msg3.data)
    }

    @Test
    fun `type can be set`() {
        val msg = Message(UPDATE)
        msg.type = FAILURE
        assertEquals(FAILURE, msg.type)
    }

    @Test
    fun `data can be reassigned`() {
        val msg = Message(UPDATE, "initial")
        msg.data = "changed"
        assertEquals("changed", msg.data)
    }

    @Test
    fun `toString contains type`() {
        val msg = Message(UPDATE, "test")
        assertTrue(msg.toString().contains(UPDATE.toString()))
    }

    @Test
    fun `null data message`() {
        val msg = Message(GAME_ROOM_CLOSE)
        assertNull(msg.data)
        assertEquals(GAME_ROOM_CLOSE, msg.type)
    }
}