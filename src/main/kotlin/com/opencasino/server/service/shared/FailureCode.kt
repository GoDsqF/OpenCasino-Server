package com.opencasino.server.service.shared

enum class FailureCode {
    ROOM_FULL,
    ROOM_NOT_FOUND,
    ROOM_CLOSED,
    INVALID_DECISION,
    INVALID_BET,
    BET_BELOW_MIN,
    INSUFFICIENT_FUNDS,
    NOT_YOUR_TURN,
    GAME_ALREADY_STARTED,
    INTERNAL
}