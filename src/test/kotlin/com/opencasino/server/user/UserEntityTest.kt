package com.opencasino.server.user

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UserEntityTest {

    @Test
    fun `defaults are sensible`() {
        val user = User(email = "alice@example.com")
        assertNotNull(user.id)
        assertFalse(user.emailVerified)
        assertNull(user.passwordHash)
        assertEquals(Role.USER, user.role)
        assertNull(user.lastLoginAt)
    }

    @Test
    fun `id is unique by default`() {
        val a = User(email = "a@example.com")
        val b = User(email = "b@example.com")
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `created and updated timestamps default to now`() {
        val before = Instant.now()
        val user = User(email = "c@example.com")
        val after = Instant.now()
        assertTrue(!user.createdAt.isBefore(before) && !user.createdAt.isAfter(after))
        assertTrue(!user.updatedAt.isBefore(before) && !user.updatedAt.isAfter(after))
    }

    @Test
    fun `custom fields are preserved`() {
        val id = UUID.randomUUID()
        val now = Instant.parse("2026-05-01T10:00:00Z")
        val user = User(
            id = id,
            email = "x@example.com",
            emailVerified = true,
            passwordHash = "bcrypt-hash",
            role = Role.ADMIN,
            createdAt = now,
            updatedAt = now,
            lastLoginAt = now,
        )
        assertEquals(id, user.id)
        assertTrue(user.emailVerified)
        assertEquals("bcrypt-hash", user.passwordHash)
        assertEquals(Role.ADMIN, user.role)
        assertEquals(now, user.lastLoginAt)
    }

    @Test
    fun `oauth-only user has null password hash`() {
        val user = User(email = "oauth@example.com", passwordHash = null)
        assertNull(user.passwordHash)
    }
}
