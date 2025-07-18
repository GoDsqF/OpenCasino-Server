package com.opencasino.server.game

import com.opencasino.server.network.pack.InitPack

interface Initializable<T : InitPack> {
    fun info(): T
}