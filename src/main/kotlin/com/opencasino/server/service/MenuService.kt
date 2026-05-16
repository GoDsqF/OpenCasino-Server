package com.opencasino.server.service

import com.opencasino.server.network.pack.menu.update.MenuUpdatePack


interface MenuService {
    fun getMenuSnapshot(): MenuUpdatePack
}