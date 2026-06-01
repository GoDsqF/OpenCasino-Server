package com.opencasino.server.network.pack.update

import com.opencasino.server.game.model.Card
import com.opencasino.server.network.pack.PublicUpdatePack
import com.opencasino.server.network.pack.UpdatePack

class PlayerHandUpdatePack(
    /** Публичные данные игрока (без balance / privacy-sensitive полей). */
    val player: PublicUpdatePack, // ts: BlackjackPublicPlayerUpdatePack | PokerPublicPlayerUpdatePack
    // null = карта рубашкой (закрытая).
    val cards: List<Card?>,
) : UpdatePack
