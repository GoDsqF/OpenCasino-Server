package com.opencasino.server.game.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class UserDataTest {

    @Test
    fun `creates with all fields`() {
        val id = UUID.randomUUID()
        val data = UserData(id, "player1", "John", "Doe", "john@test.com")
        assertEquals(id, data.id)
        assertEquals("player1", data.username)
        assertEquals("John", data.firstName)
        assertEquals("Doe", data.lastName)
        assertEquals("john@test.com", data.email)
    }

    @Test
    fun `default id is generated`() {
        val data = UserData(username = "player1", firstName = "John", lastName = "Doe")
        assertNotNull(data.id)
    }

    @Test
    fun `email defaults to null`() {
        val data = UserData(username = "player1", firstName = "John", lastName = "Doe")
        assertNull(data.email)
    }

    @Test
    fun `two instances have different default ids`() {
        val d1 = UserData(username = "p1", firstName = "A", lastName = "B")
        val d2 = UserData(username = "p2", firstName = "C", lastName = "D")
        assertNotEquals(d1.id, d2.id)
    }

    @Test
    fun `data class equality by content`() {
        val id = UUID.randomUUID()
        val d1 = UserData(id, "p1", "John", "Doe", "j@t.com")
        val d2 = UserData(id, "p1", "John", "Doe", "j@t.com")
        assertEquals(d1, d2)
    }

    @Test
    fun `data class inequality`() {
        val id = UUID.randomUUID()
        val d1 = UserData(id, "p1", "John", "Doe", "j@t.com")
        val d2 = UserData(id, "p2", "John", "Doe", "j@t.com")
        assertNotEquals(d1, d2)
    }

    @Test
    fun `copy works correctly`() {
        val original = UserData(username = "p1", firstName = "John", lastName = "Doe")
        val copy = original.copy(username = "p2")
        assertEquals("p2", copy.username)
        assertEquals(original.id, copy.id)
        assertEquals(original.firstName, copy.firstName)
    }
}