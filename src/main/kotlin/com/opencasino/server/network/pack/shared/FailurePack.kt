package com.opencasino.server.network.pack.shared

import com.opencasino.server.network.pack.InitPack

data class FailurePack(
    val code: String, // ts: FailureCode
    val message: String,
    /** Произвольные структурированные подробности (например, лимиты для BET_BELOW_MIN). */
    val details: Any? = null,
) : InitPack
