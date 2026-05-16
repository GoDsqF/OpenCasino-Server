package com.opencasino.server.game.poker.holdem.model

import kotlin.math.min

/**
 * Pure side-pot distribution.
 *
 * Inputs: per-player [PokerContestant] (id, total chips put in the pot during the hand,
 * and the player's best 5-card [PokerHand] — null for folded).
 *
 * Output: [PokerDistribution] with payouts per player (id → chips won) and the side-pot
 * breakdown for wire-level reporting.
 */
data class PokerContestant(
    val id: Long,
    val contribution: Double,
    val hand: PokerHand?,
)

data class PokerSidePot(
    val amount: Double,
    val eligibleIds: List<Long>,
    val winnerIds: List<Long>,
)

data class PokerDistribution(
    val payouts: Map<Long, Double>,
    val pots: List<PokerSidePot>,
)

object PokerSidePotDistribution {

    fun distribute(contestants: List<PokerContestant>): PokerDistribution {
        if (contestants.isEmpty()) {
            return PokerDistribution(emptyMap(), emptyList())
        }

        val payouts = HashMap<Long, Double>()
        contestants.forEach { payouts[it.id] = 0.0 }

        val remaining = HashMap<Long, Double>()
        contestants.forEach { remaining[it.id] = it.contribution }

        val pots = mutableListOf<PokerSidePot>()

        while (true) {
            val stillIn = remaining.filterValues { it > 0.0 }
            if (stillIn.isEmpty()) break

            val layer = stillIn.values.min()
            val contributors = stillIn.keys.toList()

            val potAmount = layer * contributors.size
            contributors.forEach { id -> remaining[id] = (remaining[id] ?: 0.0) - layer }

            val eligibleContestants = contributors
                .mapNotNull { id -> contestants.firstOrNull { it.id == id } }
                .filter { it.hand != null }

            if (eligibleContestants.isEmpty()) {
                // Everyone in this layer folded — bonus to next layer; merge by adding to
                // remaining of contributors still alive. Falls through if no one is alive,
                // in which case the chips are simply lost (shouldn't happen with valid input).
                pots.add(
                    PokerSidePot(
                        amount = potAmount,
                        eligibleIds = contributors,
                        winnerIds = emptyList(),
                    )
                )
                continue
            }

            val bestHand = eligibleContestants.map { it.hand!! }.max()
            val winners = eligibleContestants.filter { it.hand!!.compareTo(bestHand) == 0 }.map { it.id }

            val share = potAmount / winners.size
            val truncated = floorTo2dp(share)
            val totalTruncated = truncated * winners.size
            val remainder = round2dp(potAmount - totalTruncated)

            winners.forEachIndexed { idx, id ->
                val extra = if (idx == 0) remainder else 0.0
                payouts[id] = round2dp((payouts[id] ?: 0.0) + truncated + extra)
            }

            pots.add(
                PokerSidePot(
                    amount = potAmount,
                    eligibleIds = contributors,
                    winnerIds = winners,
                )
            )
        }

        return PokerDistribution(payouts.toMap(), pots.toList())
    }

    private fun floorTo2dp(value: Double): Double = kotlin.math.floor(value * 100.0) / 100.0
    private fun round2dp(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
}
