package com.opencasino.server.service.shared

// Фазы раунда Crash (CRASH.md §2.1). Single и multi шарят одну машину состояний;
// различие — в источнике тайминга BETTING (по ставке vs по каденции) и в settle.
enum class CrashPhase {
    WAITING,
    BETTING,
    RUNNING,
    CRASHED,
    COOLDOWN,
}
