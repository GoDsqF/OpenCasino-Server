package com.opencasino.server.service


interface MenuService {
    fun getAvailableGames(): List<String>
    fun getTotalActivePlayers(): Int
}