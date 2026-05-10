package com.opencasino.server.user

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class UserOAuthIdentityEntityTest {

    @Test
    fun `fields are preserved`() {
        val userId = UUID.randomUUID()
        val identity = UserOAuthIdentity(
            userId = userId,
            provider = "google",
            subject = "1234567890",
        )
        assertEquals(userId, identity.userId)
        assertEquals("google", identity.provider)
        assertEquals("1234567890", identity.subject)
    }

    @Test
    fun `equality is by all fields`() {
        val userId = UUID.randomUUID()
        val a = UserOAuthIdentity(userId, "google", "x")
        val b = UserOAuthIdentity(userId, "google", "x")
        val c = UserOAuthIdentity(userId, "github", "x")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
