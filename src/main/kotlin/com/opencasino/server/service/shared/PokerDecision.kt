package com.opencasino.server.service.shared

enum class PokerDecision(val type: Int) {
    CHECK(20), CALL(21), RAISE(22), FOLD(23), NONE(44)
}