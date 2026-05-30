package com.opencasino.server.game.crash.model

import com.opencasino.server.game.model.AbstractEntity
import com.opencasino.server.network.shared.PlayerSession

/**
 * Состояние игрока в раунде Crash. Балансовая модель та же, что у poker:
 * `balance` — in-memory зеркало `users.balance`, дебет/кредит дублируется в ledger
 * (CRASH.md §6). Anonymous (`userSession.userId == null`) — только in-memory.
 *
 * Per-round поля сбрасываются на открытии нового окна BETTING (`resetRound`).
 */
class CrashPlayer(
    id: Long,
    var userSession: PlayerSession,
) : AbstractEntity<Long>(id) {
    var balance: Double = 0.0
    var displayName: String = ""

    // Игрок поставил stake в текущем раунде (баланс уже дебетован).
    var boughtIn: Boolean = false
    var stake: Double = 0.0

    // Авто-cash-out: сервер сам выводит при m(t) >= target (CRASH.md §5.2).
    // null = только ручной cash-out.
    var autoCashoutTarget: Double? = null

    // Множитель, на котором игрок вывелся. null = ещё в игре / проиграл при обвале.
    var cashedOutAt: Double? = null
    var payout: Double = 0.0

    var disconnected: Boolean = false
}
