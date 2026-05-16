package com.opencasino.server.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeriveDisplayNameForOAuthTest {

    private val blocklist = listOf("admin", "root", "moderator")

    private fun derive(profileName: String?, email: String?): String =
        AuthService.deriveDisplayNameForOAuth(profileName, email, blocklist)

    @Test fun `clean profile name passes through`() =
        assertEquals("alice", derive("alice", "alice@example.com"))

    @Test fun `space inside profile name is stripped`() =
        assertEquals("JohnDoe", derive("John Doe", "john@example.com"))

    @Test fun `cyrillic letters are stripped, fallback to email`() =
        assertEquals("alice", derive("Алиса", "alice@example.com"))

    @Test fun `dots and at-signs in profile name are stripped`() =
        assertEquals("alicebob", derive("alice.bob", "x@example.com"))

    @Test fun `too short after sanitize falls back to email`() =
        assertEquals("alice", derive("a!", "alice@example.com"))

    @Test fun `null profile name uses email local-part`() =
        assertEquals("alice", derive(null, "alice@example.com"))

    @Test fun `denylisted profile name falls back to email`() =
        assertEquals("alice42", derive("admin", "alice42@example.com"))

    @Test fun `denylisted profile and denylisted email yield default`() =
        assertEquals(AuthService.DEFAULT_OAUTH_DISPLAY_NAME, derive("admin", "root@x.com"))

    @Test fun `null email and unusable profile yield default`() =
        assertEquals(AuthService.DEFAULT_OAUTH_DISPLAY_NAME, derive("!", null))

    @Test fun `overlong profile is truncated to 32 chars`() =
        assertEquals("a".repeat(32), derive("a".repeat(50), "x@y.com"))

    @Test fun `email-only without verified profile name`() =
        assertEquals("bob_42", derive(null, "bob_42@example.com"))

    @Test fun `default never collides with denylist when default itself isn't denylisted`() =
        assertEquals(AuthService.DEFAULT_OAUTH_DISPLAY_NAME, derive(null, null))
}
