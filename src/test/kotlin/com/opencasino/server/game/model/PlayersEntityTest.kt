package com.opencasino.server.game.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class PlayersEntityTest {

    @Test
    fun `default constructor creates valid entity`() {
        val player = Players(
            username = "testuser",
            firstName = "John",
            lastName = "Doe",
            email = "john@test.com"
        )
        assertNotNull(player.id)
        assertEquals("testuser", player.username)
        assertEquals("John", player.firstName)
        assertEquals("Doe", player.lastName)
        assertEquals("john@test.com", player.email)
    }

    @Test
    fun `default balance is zero`() {
        val player = Players(
            username = "testuser",
            firstName = "John",
            lastName = "Doe",
            email = "john@test.com"
        )
        assertEquals(0.00, player.balance)
    }

    @Test
    fun `custom balance is preserved`() {
        val player = Players(
            username = "richguy",
            balance = 10000.50,
            firstName = "Rich",
            lastName = "Guy",
            email = "rich@test.com"
        )
        assertEquals(10000.50, player.balance)
    }

    @Test
    fun `userHash defaults to null`() {
        val player = Players(
            username = "testuser",
            firstName = "John",
            lastName = "Doe",
            email = null
        )
        assertNull(player.userHash)
    }

    @Test
    fun `email can be null`() {
        val player = Players(
            username = "testuser",
            firstName = "John",
            lastName = "Doe",
            email = null
        )
        assertNull(player.email)
    }

    @Test
    fun `createdAt is set automatically`() {
        val before = System.currentTimeMillis() / 1000 - 1
        val player = Players(
            username = "testuser",
            firstName = "John",
            lastName = "Doe",
            email = "john@test.com"
        )
        val after = System.currentTimeMillis() / 1000 + 1
        assertTrue(player.createdAt in before..after)
    }

    @Test
    fun `lastModified is set automatically`() {
        val before = System.currentTimeMillis() / 1000 - 1
        val player = Players(
            username = "testuser",
            firstName = "John",
            lastName = "Doe",
            email = "john@test.com"
        )
        val after = System.currentTimeMillis() / 1000 + 1
        assertTrue(player.lastModified in before..after)
    }

    @Test
    fun `custom id is preserved`() {
        val customId = "custom-uuid-12345"
        val player = Players(
            id = customId,
            username = "testuser",
            firstName = "John",
            lastName = "Doe",
            email = "john@test.com"
        )
        assertEquals(customId, player.id)
    }

    @Test
    fun `toString contains all fields`() {
        val player = Players(
            id = "test-id",
            username = "testuser",
            balance = 100.0,
            firstName = "John",
            lastName = "Doe",
            email = "john@test.com",
            userHash = "hash123"
        )
        val str = player.toString()
        assertTrue(str.contains("test-id"))
        assertTrue(str.contains("testuser"))
        assertTrue(str.contains("100.0"))
        assertTrue(str.contains("John"))
        assertTrue(str.contains("Doe"))
        assertTrue(str.contains("john@test.com"))
        assertTrue(str.contains("hash123"))
    }

    @Test
    fun `two players with different ids are different`() {
        val p1 = Players(id = "id-1", username = "a", firstName = "A", lastName = "A", email = "a@a.com")
        val p2 = Players(id = "id-2", username = "a", firstName = "A", lastName = "A", email = "a@a.com")
        assertNotEquals(p1.id, p2.id)
    }
}