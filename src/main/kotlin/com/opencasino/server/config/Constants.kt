package com.opencasino.server.config

const val GAME_TASK_MANAGER = "gameTaskManager"

const val DEFAULT_LOOP_RATE = 300L//300L
const val ROOM_START_DELAY = 5000L
const val ROOM_END_DELAY = 1000L * 60 * 5
const val ROOM_INIT_DELAY = 5000L

const val MAX_BLACKJACK_PLAYERS = 1
const val MIN_BLACKJACK_BET = 20.00
const val BLACKJACK_DECK_STACKS = 8
const val BLACKJACK_RESHUFFLE_THRESHOLD = 64

const val MAX_POKER_PLAYERS = 6
const val MIN_POKER_PLAYERS = 2
const val MIN_POKER_BET = 50.00
const val MAX_POKER_BET = 100.00
const val POKER_BUY_IN = 2000
const val POKER_DECK_STACKS = 8