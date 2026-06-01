package com.opencasino.server.event.poker

import com.opencasino.server.event.AbstractEvent

data class GameSettingsUpdateEvent(
    val betType: String?, // ts: PokerBetType | null
    // Big blind / базовая ставка раунда.
    val bet: Double,
    val minLimit: Double?,
    val maxLimit: Double?,
    /** Per-room override для `PokerRoomProperties.maxPlayers`. null = взять глобальный дефолт.
     *  Применяется только при создании стола (см. PokerRoomServiceImpl.addPlayerToWait).
     *  Любое последующее переоткрытие GameSettingsUpdateEvent игнорируется. */
    val maxPlayers: Int? = null,
    /** Нижняя граница buy-in для этого стола. null = `PokerRoomProperties.buyIn`. */
    val minBuyIn: Double? = null,
    /** Верхняя граница buy-in. null = без верхнего ограничения (только balance). */
    val maxBuyIn: Double? = null,
) : AbstractEvent()
