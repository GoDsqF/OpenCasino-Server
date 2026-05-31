package com.opencasino.server.rng

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import java.util.UUID

class RandomnessServiceTest {
    private val repository = mock<ProvablyFairRoundRepository>()
    private val provider = CrashOutcomeProvider(RngProfile(houseEdge = 0.03, maxPayout = 1000.0))
    private val service = RandomnessService(repository, ProvablyFairChain.random(1000))

    @Test
    fun `commit persists hash with no revealed seed and returns outcome`() {
        whenever(repository.insert(any())).thenAnswer { Mono.just(it.arguments[0] as ProvablyFairRound) }

        val commit = service.commit("CRASH", "client-1", provider).block()!!

        val captor = argumentCaptor<ProvablyFairRound>()
        verify(repository).insert(captor.capture())
        val saved = captor.firstValue
        assertNull(saved.revealedSeed)
        assertEquals(commit.serverSeedHash, saved.serverSeedHash)
        assertEquals(commit.outcome.toString(), saved.outcome)
        assertEquals(0.03, saved.houseEdge)
        assertTrue(commit.outcome >= 1.0)
    }

    @Test
    fun `verify accepts honest reveal and rejects tampering`() {
        whenever(repository.insert(any())).thenAnswer { Mono.just(it.arguments[0] as ProvablyFairRound) }
        whenever(repository.markRevealed(any(), any())).thenReturn(Mono.just(1L))

        val commit = service.commit("CRASH", "client-x", provider).block()!!
        val serverSeedHex = service.reveal(commit.roundId).block()!!

        fun verifyWith(
            seed: String,
            outcome: Double,
        ) = service.verify(seed, commit.serverSeedHash, "client-x", commit.roundId, outcome, provider)

        val tampered = serverSeedHex.replaceFirst(serverSeedHex[0], if (serverSeedHex[0] == 'a') 'b' else 'a')
        assertTrue(verifyWith(serverSeedHex, commit.outcome))
        assertFalse(verifyWith(tampered, commit.outcome)) // подменён seed
        assertFalse(verifyWith(serverSeedHex, commit.outcome + 1.0)) // подменён outcome
    }

    @Test
    fun `reveal of unknown round returns empty`() {
        assertNull(service.reveal(UUID.randomUUID()).block())
    }

    @Test
    fun `reserve publishes hash without persisting and derive completes the round`() {
        whenever(repository.insert(any())).thenAnswer { Mono.just(it.arguments[0] as ProvablyFairRound) }

        val reservation = service.reserve().block()!!
        // reserve не пишет аудит — clientSeed/outcome ещё не известны.
        verify(repository, org.mockito.kotlin.never()).insert(any())

        val commit = service.derive(reservation.roundId, "CRASH", "bets-hash", provider).block()!!

        assertEquals(reservation.roundId, commit.roundId)
        assertEquals(reservation.serverSeedHash, commit.serverSeedHash)
        val captor = argumentCaptor<ProvablyFairRound>()
        verify(repository).insert(captor.capture())
        assertEquals("bets-hash", captor.firstValue.clientSeed)
        assertEquals(commit.outcome.toString(), captor.firstValue.outcome)
        assertTrue(commit.outcome >= 1.0)
    }

    @Test
    fun `reserve then derive produces a verifiable bet-derived outcome`() {
        whenever(repository.insert(any())).thenAnswer { Mono.just(it.arguments[0] as ProvablyFairRound) }
        whenever(repository.markRevealed(any(), any())).thenReturn(Mono.just(1L))

        val reservation = service.reserve().block()!!
        val commit = service.derive(reservation.roundId, "CRASH", "bets-hash", provider).block()!!
        val serverSeedHex = service.reveal(commit.roundId).block()!!

        assertTrue(
            service.verify(serverSeedHex, commit.serverSeedHash, "bets-hash", commit.roundId, commit.outcome, provider),
        )
    }
}
