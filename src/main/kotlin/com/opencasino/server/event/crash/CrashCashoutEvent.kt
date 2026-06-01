package com.opencasino.server.event.crash

import com.opencasino.server.event.AbstractEvent

/**
 * «Выведи меня сейчас». Несёт только `lastTickSeq` — кадр UPDATE, который игрок видел
 * в момент клика. Число (множитель) НЕ принимаем (CRASH.md §0, §5.3): сервер
 * засчитывает множитель кадра, который сам же выпустил, — abuse-resistant.
 */
data class CrashCashoutEvent(
    val lastTickSeq: Long = 0,
) : AbstractEvent()
