package com.opencasino.server.config

const val GAME_TASK_MANAGER = "gameTaskManager"

const val DEFAULT_LOOP_RATE = 300L // 300L
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

// Сколько комната держит post-showdown состояние перед resetTable: даёт FE время
// проиграть staggered reveal оппонента, подсветку combo и chip-fly. Меньше — у FE
// не успевают сработать reveal-таймеры (350ms + flip 420ms + chip-fly ~850ms),
// и игрок видит только то, что 5 карт лежат, а раунд «пропал». 3.5с — достаточно
// для текущей FE-анимации и не перегружает темп игры.
const val SHOWDOWN_REVEAL_MS = 3500L

const val DISCONNECT_GRACE_MS = 60_000L
