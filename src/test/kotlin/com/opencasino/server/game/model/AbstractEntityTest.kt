package com.opencasino.server.game.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AbstractEntityTest {

    // Конкретная реализация для тестирования
    class TestEntity(id: Long) : AbstractEntity<Long>(id)

    @Test
    fun `entities with same id are equal`() {
        val e1 = TestEntity(42L)
        val e2 = TestEntity(42L)
        assertEquals(e1, e2)
    }

    @Test
    fun `entities with different ids are not equal`() {
        val e1 = TestEntity(1L)
        val e2 = TestEntity(2L)
        assertNotEquals(e1, e2)
    }

    @Test
    fun `entity equals itself`() {
        val e = TestEntity(1L)
        assertEquals(e, e)
    }

    @Test
    fun `entity not equals null`() {
        val e = TestEntity(1L)
        assertNotEquals(null, e)
    }

    @Test
    fun `entity not equals different class`() {
        val e = TestEntity(1L)
        assertNotEquals(e, "string")
    }

    @Test
    fun `same id produces same hashCode`() {
        val e1 = TestEntity(42L)
        val e2 = TestEntity(42L)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    @Test
    fun `different ids produce different hashCodes`() {
        val e1 = TestEntity(1L)
        val e2 = TestEntity(2L)
        assertNotEquals(e1.hashCode(), e2.hashCode())
    }

    @Test
    fun `id is accessible`() {
        val e = TestEntity(99L)
        assertEquals(99L, e.id)
    }

    @Test
    fun `equals is symmetric`() {
        val e1 = TestEntity(5L)
        val e2 = TestEntity(5L)
        assertTrue(e1 == e2 && e2 == e1)
    }

    @Test
    fun `equals is transitive`() {
        val e1 = TestEntity(5L)
        val e2 = TestEntity(5L)
        val e3 = TestEntity(5L)
        assertTrue(e1 == e2 && e2 == e3 && e1 == e3)
    }
}