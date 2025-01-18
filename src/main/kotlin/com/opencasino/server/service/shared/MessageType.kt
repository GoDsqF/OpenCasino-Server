package com.opencasino.server.service.shared

enum class MessageType(val type: Int) {
    SYSTEM(1), ROOM(2), PRIVATE(3), ADMIN(4)
}