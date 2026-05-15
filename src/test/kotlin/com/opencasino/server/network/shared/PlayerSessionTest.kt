package com.opencasino.server.network.shared

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.web.reactive.socket.HandshakeInfo
import java.util.*

class PlayerSessionTest {

    private val mockHandshake = mock(HandshakeInfo::class.java)

    @Test
    fun `session stores id`() {
        val session = PlayerSession("test-id", mockHandshake)
        assertEquals("test-id", session.id)
    }

    @Test
    fun `initial player is null`() {
        val session = PlayerSession("test-id", mockHandshake)
        assertNull(session.player)
    }

    @Test
    fun `initial roomKey is null`() {
        val session = PlayerSession("test-id", mockHandshake)
        assertNull(session.roomKey)
    }

    @Test
    fun `initial serviceId is null`() {
        val session = PlayerSession("test-id", mockHandshake)
        assertNull(session.serviceId)
    }

    @Test
    fun `initial principal is null`() {
        val session = PlayerSession("test-id", mockHandshake)
        assertNull(session.principal)
    }

    @Test
    fun `userId derives from principal name when it is a UUID`() {
        val session = PlayerSession("test-id", mockHandshake)
        val uuid = UUID.randomUUID()
        session.principal = java.security.Principal { uuid.toString() }
        assertEquals(uuid, session.userId)
    }

    @Test
    fun `userId is null when principal name is not a UUID`() {
        val session = PlayerSession("test-id", mockHandshake)
        session.principal = java.security.Principal { "not-a-uuid" }
        assertNull(session.userId)
    }

    @Test
    fun `userId is null when principal is not set`() {
        val session = PlayerSession("test-id", mockHandshake)
        assertNull(session.userId)
    }

    @Test
    fun `roomKey can be set`() {
        val session = PlayerSession("test-id", mockHandshake)
        val uuid = UUID.randomUUID()
        session.roomKey = uuid
        assertEquals(uuid, session.roomKey)
    }

    @Test
    fun `serviceId can be set`() {
        val session = PlayerSession("test-id", mockHandshake)
        session.serviceId = "Blackjack"
        assertEquals("Blackjack", session.serviceId)
    }

    @Test
    fun `equals based on id`() {
        val s1 = PlayerSession("same-id", mockHandshake)
        val s2 = PlayerSession("same-id", mockHandshake)
        assertEquals(s1, s2)
    }

    @Test
    fun `not equals with different id`() {
        val s1 = PlayerSession("id-1", mockHandshake)
        val s2 = PlayerSession("id-2", mockHandshake)
        assertNotEquals(s1, s2)
    }

    @Test
    fun `hashCode consistent with equals`() {
        val s1 = PlayerSession("same-id", mockHandshake)
        val s2 = PlayerSession("same-id", mockHandshake)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun `toString contains id`() {
        val session = PlayerSession("abc-123", mockHandshake)
        assertTrue(session.toString().contains("abc-123"))
    }
}