package com.opencasino.server.network.pack.update

import com.opencasino.server.network.pack.UpdatePack

interface IUpdatePackProvider<T: UpdatePack> {
    fun getUpdatePack(): T
}