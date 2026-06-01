package com.opencasino.server.event

data class BetEvent(
    /** Blackjack — ставка раунда; Poker — buy-in; Crash — ставка в окне BETTING. */
    val bet: Double,
    // Crash: опциональный авто-cash-out (CRASH.md §5.2). Сервер сам выводит при
    // m(t) >= target. null / <= 1.0 = только ручной cash-out. Игнорируется не-crash
    // играми (poker buy-in / blackjack ставка его не читают).
    val autoCashoutTarget: Double? = null,
) : AbstractEvent()
