package com.opencasino.server.network.pack.blackjack.init

import com.opencasino.server.network.pack.InitPack

interface IInitPackProvider<T: InitPack> {
    fun getInitPack(): T
}