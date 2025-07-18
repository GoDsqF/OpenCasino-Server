package com.opencasino.server.network.pack

interface IInitPackProvider<T: InitPack> {
    fun getInfoPack(): T
}