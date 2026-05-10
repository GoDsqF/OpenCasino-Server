package com.opencasino.server.user

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RoleTest {

    @Test
    fun `enum exposes USER and ADMIN`() {
        assertEquals(setOf("USER", "ADMIN"), Role.entries.map { it.name }.toSet())
    }

    @Test
    fun `valueOf round-trips through the string name`() {
        assertEquals(Role.USER, Role.valueOf("USER"))
        assertEquals(Role.ADMIN, Role.valueOf("ADMIN"))
    }
}
