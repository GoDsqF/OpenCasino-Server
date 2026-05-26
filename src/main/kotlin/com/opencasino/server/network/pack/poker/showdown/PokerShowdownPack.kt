package com.opencasino.server.network.pack.poker.showdown

import com.opencasino.server.game.model.Card
import com.opencasino.server.network.pack.Pack

data class PokerShowdownEntry(
    val id: Long,
    val payout: Double,
    val handCategory: String?,
    val handCards: List<Card>?,
    val holeCards: List<Card>?,
)

data class PokerShowdownSidePot(
    val amount: Double,
    val eligibleIds: List<Long>,
    val winnerIds: List<Long>,
)

data class PokerShowdownPack(
    val entries: List<PokerShowdownEntry>,
    val pots: List<PokerShowdownSidePot>,
    // Объединённый highlight-set по всем победителям (карты их лучших комбинаций,
    // dedup). Раньше FE сам мёрджил handCards по entries и руками дедапил для
    // split-pot — сервер и так знает финальный набор, отдаём готовым. Пусто, если
    // вскрытия не было (один не-сфолдивший игрок забрал банк без шоудауна).
    val boardHighlight: List<Card>,
) : Pack
