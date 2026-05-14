package com.opencasino.server.network.pack.shared

import com.opencasino.server.network.pack.InitPack

data class FailurePack(
    val code: String,
    val message: String,
    val details: Any? = null
) : InitPack