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

// Сколько busted-игрок (stack<=0) остаётся за столом как observer после раздачи,
// прежде чем комната сама его высадит (auto-LEAVE + GAME_ROOM_CLOSE). Окно на
// принятие решения «re-buy или ухожу»: за это время BET возвращает игрока в
// игру со следующего раунда. Короче DISCONNECT_GRACE_MS — observer присутствует
// сознательно (видит стол), а не из-за сетевого разрыва.
const val OBSERVER_GRACE_MS = 30_000L

// Сколько у актора есть на ход, прежде чем сервер сам сходит за него
// (Poker: fold, либо check если коллировать нечего; Blackjack: stand текущей руки)
// и сдвинет игру дальше. Это authoritative turn-deadline: сервер штампует
// `turnDeadlineEpochMs = actorTurnStartedAt + ACTION_TIMEOUT_MS` в UPDATE, чтобы
// все клиенты крутили один и тот же countdown (а не свой от приёма пакета).
//
// Значение намеренно ОЧЕНЬ большое: механизм заведён сейчас (раньше турн-таймера
// не было вообще — ход висел бесконечно, AFK-игрок морозил стол), но порог держим
// высоким, чтобы он бил только по брошенным столам и не менял темп живой игры.
// Ужесточать — отдельной правкой, когда на FE появится turn-countdown UX.
const val ACTION_TIMEOUT_MS = 10L * 60 * 1000 // 10 минут

// RNG / provably-fair house edge per game. `e=0.03` → RTP 97% (см. CRASH.md §1.3).
const val CRASH_HOUSE_EDGE = 0.03

// Капа на crashPoint: обвал гарантирован не позже неё, одинаково для всех игроков.
const val CRASH_MAX_PAYOUT = 1000.0
