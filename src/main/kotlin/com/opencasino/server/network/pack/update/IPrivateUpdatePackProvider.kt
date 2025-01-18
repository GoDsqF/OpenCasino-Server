package com.opencasino.server.network.pack.update

import com.opencasino.server.network.pack.PrivateUpdatePack

interface IPrivateUpdatePackProvider<T: PrivateUpdatePack> {
    fun getPrivateUpdatePack(): T
}