package com.opencasino.server.math

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class UtilsTest {

    @RepeatedTest(50)
    fun `getRandomIndex returns value within range`() {
        val max = 10
        val result = getRandomIndex(max)
        assertTrue(result in 0 until max, "Expected 0..${max-1}, got $result")
    }

    @Test
    fun `getRandomIndex with max 1 returns 0`() {
        // С max=1 диапазон [0, 1), единственное int значение это 0
        repeat(20) {
            assertEquals(0, getRandomIndex(1))
        }
    }

    @RepeatedTest(50)
    fun `getRandomNumber int returns value in range`() {
        val min = 5
        val max = 15
        val result = getRandomNumber(min, max)
        assertTrue(result in min until max, "Expected $min..${max-1}, got $result")
    }

    @RepeatedTest(50)
    fun `getRandomNumber double returns value in range`() {
        val min = 1.0
        val max = 10.0
        val result = getRandomNumber(min, max)
        assertTrue(result >= min && result < max, "Expected [$min, $max), got $result")
    }

    @Test
    fun `getRandomNumber with same min max returns min`() {
        val result = getRandomNumber(5, 5)
        assertEquals(5, result)
    }

    @Test
    fun `getRandomNumber double with same min max returns min`() {
        val result = getRandomNumber(3.0, 3.0)
        assertEquals(3.0, result)
    }

    @Test
    fun `mapType is not null`() {
        assertNotNull(mapType)
    }

    @RepeatedTest(20)
    fun `getRandomIndex produces varied results over many calls`() {
        val results = (1..100).map { getRandomIndex(100) }.toSet()
        // С 100 попытками при max=100 должно быть больше 1 уникального значения
        assertTrue(results.size > 1, "Expected varied results, got ${results.size} unique values")
    }
}