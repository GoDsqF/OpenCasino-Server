package com.opencasino.server.service.shared

// High-level lifecycle-фаза клиента — единый источник истины вместо локальной
// Phase-машины на каждой игровой странице (комбинация socket.status + joined +
// boughtIn + availableActions). Сервер вычисляет её per-recipient и кладёт в
// *UpdatePack.clientState.
//
// Сервер в UPDATE эмитит только server-knowable подмножество:
//   AWAITING_BUY_IN — сидит, но не профинансирован (observer / late join без buy-in);
//   AWAITING_START  — профинансирован, игра ещё не запущена (ждём остальных);
//   IN_ROUND        — раздача идёт, ход не его;
//   AWAITING_TURN   — раздача идёт, сейчас его ход (есть availableActions);
//   SHOWDOWN        — окно вскрытия / расчёта.
// CONNECTING / JOINING / CLOSED — socket-уровень: до/после UPDATE-потока их
// выводит сам клиент из состояния сокета, сервер их не присылает. Оставлены в
// enum для полноты протокольного контракта (FE-машина 1:1).
enum class ClientState {
    CONNECTING,
    JOINING,
    AWAITING_BUY_IN,
    AWAITING_START,
    IN_ROUND,
    AWAITING_TURN,
    SHOWDOWN,
    CLOSED,
}
