package com.opencasino.server.game.crash.room

import com.opencasino.server.config.CrashRoomProperties
import com.opencasino.server.rng.ProvablyFairChain
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.rng.RngProfile
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.user.BalanceLedgerService
import reactor.core.scheduler.Scheduler
import java.security.MessageDigest
import java.util.UUID

/**
 * Multi-режим Crash (CRASH.md §2): одна общая комната на N игроков с непрерывной
 * каденцией. Кривая/тайминг раунда общие для всех; settle — per-player. FSM/тик-луп/
 * RNG/ledger/cash-out живут в [AbstractCrashGameRoom]; здесь — только триггер фазы
 * BETTING по каденции, вместимость и сорсинг bet-derived clientSeed.
 *
 * Каденция: окно BETTING открывается тиком (а не приходом ставки, [allowOpenOnBet] =
 * false), и сразу переоткрывается на выходе из COOLDOWN. Пустая комната простаивает в
 * WAITING — не жжём seed'ы хэш-цепочки и не шлём апдейты в никуда (CRASH.md §10, R4).
 */
open class MultiCrashGameRoom(
    gameRoomId: UUID,
    schedulerService: Scheduler,
    roomService: RoomService,
    webSocketSessionService: WebSocketSessionService,
    randomnessService: RandomnessService,
    roomProperties: CrashRoomProperties,
    rngProfile: RngProfile,
    ledgerService: BalanceLedgerService? = null,
) : AbstractCrashGameRoom(
        gameRoomId,
        schedulerService,
        roomService,
        webSocketSessionService,
        randomnessService,
        roomProperties,
        rngProfile,
        ledgerService,
    ) {
    override val maxPlayers = roomProperties.maxPlayers
    override val mode = "MULTI"

    // crashPoint выводится на закрытии окна BETTING из ставок раунда (см. buildClientSeed).
    override fun outcomeDerivedAtRunStart(): Boolean = true

    // Окно ставок открывается каденцией, не приходом ставки.
    override fun allowOpenOnBet(): Boolean = false

    // Пустая комната не крутит раунды; первый севший игрок запускает каденцию.
    override fun onWaitingTick(now: Long) {
        if (currentPlayersCount() > 0) openBetting(now)
    }

    // Непрерывная каденция: COOLDOWN истёк → сразу новое окно (если есть игроки),
    // иначе остаёмся в WAITING до следующего join'а.
    override fun onCooldownComplete(now: Long) {
        if (currentPlayersCount() > 0) openBetting(now)
    }

    // clientSeed = SHA-256 от отсортированных отпечатков ставок раунда (CRASH.md §11).
    // serverSeedHash уже опубликован при reserve → сервер не мог подобрать seed под
    // эти ставки. roundId довязывается в HMAC-сообщении (RandomnessService). Пустое
    // окно (никто не поставил) → серверная соль, seed всё равно расходуется и
    // раскрывается (без разрыва хэш-цепочки).
    override fun buildClientSeed(): String {
        val fingerprints =
            players()
                .filter { it.boughtIn }
                .map { "${it.id}:${it.stake}" }
                .sorted()
        if (fingerprints.isEmpty()) return UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(fingerprints.joinToString("|").toByteArray())
        return ProvablyFairChain.toHex(digest)
    }
}
