package com.opencasino.server.game.crash.room

import com.opencasino.server.config.CrashRoomProperties
import com.opencasino.server.rng.RandomnessService
import com.opencasino.server.rng.RngProfile
import com.opencasino.server.service.RoomService
import com.opencasino.server.service.WebSocketSessionService
import com.opencasino.server.user.BalanceLedgerService
import reactor.core.scheduler.Scheduler
import java.util.UUID

/**
 * Single-режим Crash (CRASH.md §2): комната-на-игрока, раунд открывается приходом
 * ставки. Вся FSM/тик-луп/RNG/ledger живут в [AbstractCrashGameRoom]; здесь только
 * фиксируем одно место и режим. Дефолтный `allowOpenOnBet() = true` открывает окно
 * BETTING по ставке, no-op `onCooldownComplete` возвращает комнату в WAITING до
 * следующей ставки.
 */
class SingleCrashGameRoom(
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
    override val maxPlayers = 1
    override val mode = "SINGLE"
}
