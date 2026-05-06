package com.opencasino.server.network.shared;

import com.opencasino.server.service.shared.MessageType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MessageTypeTest {

    @Test
    fun `SYSTEM has type 1`() {
        assertEquals(1, MessageType.SYSTEM.type)
    }

    @Test
    fun `ROOM has type 2`() {
        assertEquals(2, MessageType.ROOM.type)
    }

    @Test
    fun `PRIVATE has type 3`() {
        assertEquals(3, MessageType.PRIVATE.type)
    }

    @Test
    fun `ADMIN has type 4`() {
        assertEquals(4, MessageType.ADMIN.type)
    }

    @Test
    fun `all types are unique`() {
        val types = MessageType.entries.map { it.type }
        assertEquals(types.size, types.toSet().size)
    }

    @Test
    fun `four message types exist`() {
        assertEquals(4, MessageType.entries.size)
    }
}
