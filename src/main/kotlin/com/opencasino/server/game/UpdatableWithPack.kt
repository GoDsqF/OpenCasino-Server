package com.opencasino.server.game

import com.opencasino.server.network.pack.UpdatePack

interface UpdatableWithPack<T : UpdatePack> {
    fun update(): T
}