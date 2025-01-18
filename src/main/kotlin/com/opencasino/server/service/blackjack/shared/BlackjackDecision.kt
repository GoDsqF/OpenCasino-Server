package com.opencasino.server.service.blackjack.shared

enum class BlackjackDecision(val type: Int) {
    HIT(20), STAND(21), DOUBLE(22), SPLIT(23), NONE(44)
}