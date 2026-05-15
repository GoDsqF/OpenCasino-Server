package com.opencasino.server.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DisplayNameValidationTest {

    private val blocklist = listOf("admin", "root", "moderator")

    private fun validate(name: String?): String? = AuthService.normalizeDisplayName(name, blocklist)

    @Test fun `null is rejected`() = assertNull(validate(null))
    @Test fun `blank is rejected`() = assertNull(validate("   "))
    @Test fun `too short is rejected`() = assertNull(validate("ab"))
    @Test fun `too long is rejected`() = assertNull(validate("a".repeat(33)))
    @Test fun `space inside is rejected`() = assertNull(validate("alice bob"))
    @Test fun `dot inside is rejected`() = assertNull(validate("alice.bob"))
    @Test fun `cyrillic is rejected`() = assertNull(validate("алиса"))
    @Test fun `denylisted prefix is rejected case-insensitive`() = assertNull(validate("AdminBob"))
    @Test fun `denylisted suffix is rejected`() = assertNull(validate("xx-root"))
    @Test fun `denylisted infix is rejected`() = assertNull(validate("SuperAdmin42"))

    @Test fun `acceptable name passes`() = assertEquals("alice_42", validate("alice_42"))
    @Test fun `name is trimmed`() = assertEquals("alice", validate("  alice  "))
    @Test fun `minimum length passes`() = assertEquals("abc", validate("abc"))
    @Test fun `maximum length passes`() = assertEquals("a".repeat(32), validate("a".repeat(32)))

    @Test
    fun `empty blocklist allows otherwise-reserved word`() {
        assertEquals("admin", AuthService.normalizeDisplayName("admin", emptyList()))
    }

    @Test
    fun `blank entries in blocklist are ignored`() {
        assertEquals("alice", AuthService.normalizeDisplayName("alice", listOf("", "   ")))
    }
}
