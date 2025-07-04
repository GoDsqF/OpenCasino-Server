package com.opencasino.server.service.shared

enum class BlackjackDecision(val type: Int) {
    HIT(20), STAND(21), DOUBLE(22), SPLIT(23), NONE(44)
}