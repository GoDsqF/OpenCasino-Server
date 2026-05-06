# 🎰 OpenCasino Server

**OpenCasino** — это серверное приложение для онлайн-казино с открытым исходным кодом, реализованное на **Kotlin** с использованием **Spring Boot WebFlux**. Сервер поддерживает многопользовательские игры в реальном времени через WebSocket-соединения, включая **Blackjack** и **Texas Hold'em Poker**.

---

## 📋 Оглавление

- [Обзор проекта](#-обзор-проекта)
- [Архитектура](#-архитектура)
- [Основной функционал](#-основной-функционал)
- [Технологический стек](#-технологический-стек)
- [Требования](#-требования)
- [Установка и настройка](#-установка-и-настройка)
- [Запуск](#-запуск)
- [WebSocket API](#-websocket-api)
- [Структура проекта](#-структура-проекта)
- [Конфигурация](#-конфигурация)
- [База данных](#-база-данных)
- [Аутентификация](#-аутентификация)
- [Примеры использования](#-примеры-использования)

---

## 🎯 Обзор проекта

OpenCasino Server — это реактивный игровой сервер, обеспечивающий:

- **Многопользовательские игры в реальном времени** через WebSocket
- **Blackjack** — классическая карточная игра против дилера
- **Texas Hold'em Poker** — многопользовательский покер с поддержкой различных типов ставок (Fixed Limit, Pot Limit, No Limit)
- **Система аутентификации** через Google OAuth2
- **Реактивное взаимодействие с базой данных** PostgreSQL через R2DBC
- **Игровой цикл (game loop)** с настраиваемой частотой обновления

---

## 🏗 Архитектура

Проект построен по многослойной архитектуре:

1. WebSocket Layer - MainWebSocketHandler, UserSession, Auth
2. Service Layer - RoomService, SessionService, AuthService
3. Game Logic Layer - GameRoom, Player, CardDeck, PokerHand
4. Data Layer - PlayerRepository, R2DBC, PostgreSQL

### Ключевые паттерны:
- Factory Pattern — создание игроков (BlackjackPlayerFactory, PokerPlayerFactory)
- Observer/Publisher Pattern — рассылка обновлений через WebSocketMessagePublisher
- Game Loop Pattern — периодическое обновление состояния комнат через Scheduler
- Pack/DTO Pattern — разделение внутренней модели и сетевого представления (InitPack, UpdatePack, PrivateUpdatePack)

---

## 🎮 Основной функционал

### 🃏 Blackjack

- Игра один на один против дилера
- Колода из 8 стандартных колод (416 карт) с автоматической перетасовкой
- Поддерживаемые решения игрока:
    - HIT — взять ещё одну карту
    - STAND — остановиться
    - DOUBLE — удвоить ставку (в разработке)
    - SPLIT — разделить руку (в разработке)
- Автоматическая логика дилера (берёт карты до 17 очков)
- Определение условий победы:
    - PlayerWin — игрок выиграл
    - PlayerWinBlackjack — игрок получил блэкджек (21 очко с 2 карт)
    - DealerWin — дилер выиграл
    - DealerBlackjack — дилер получил блэкджек
    - Draw — ничья
- Автоматический сброс стола и начало новой игры

### ♠️ Texas Hold'em Poker

- Многопользовательская игра (2–6 игроков)
- Система создания и присоединения к комнатам
- Типы ставок:
    - Fixed Limit — фиксированные лимиты
    - Pot Limit — ограничение банком
    - No Limit — без ограничений
- Решения игрока:
    - CHECK — проверить
    - CALL — уравнять ставку
    - RAISE — повысить ставку
    - FOLD — сбросить карты
    - ALL_IN — ва-банк
- Buy-in система — вход в игру через покупку фишек
- Блайнды (малый/большой) с автоматической ротацией
- Оценка покерных комбинаций:
    - Straight Flush, Four of a Kind, Full House, Flush, Straight, Three of a Kind, Two Pair, Pair, High Card
- Скрытие карт других игроков (каждый видит только свои карты)
- Валидация ставок

### 🌐 Сетевое взаимодействие

- WebSocket — двунаправленная связь в реальном времени
- Реактивная модель (Spring WebFlux + Project Reactor)
- Система сессий и управление подключениями
- Широковещательные (broadcast) и адресные сообщения
- Система очередей ожидания для подбора игроков
- JSON-сериализация через Gson

### 🔐 Аутентификация

- Google OAuth2 через Google API Client
- Обмен авторизационного кода на токен
- Верификация ID-токена

### 📊 Главное меню

- Список доступных игр
- Количество активных игроков

---

## 🛠 Технологический стек

| Технология | Назначение |
|---|---|
| Kotlin 1.9.25 | Основной язык разработки |
| Java 21 | Целевая платформа (Virtual Threads) |
| Spring Boot 3.4.1 | Фреймворк приложения |
| Spring WebFlux | Реактивный веб-сервер |
| Project Reactor | Реактивные потоки |
| R2DBC (PostgreSQL) | Реактивный доступ к БД |
| PostgreSQL | СУБД |
| Gson | JSON-сериализация |
| Google API Client | OAuth2-аутентификация |
| Gradle 8.11.1 | Система сборки |
| Shadow Plugin | Сборка fat-JAR |

---

## 📦 Требования

- JDK 21+ (требуется поддержка Virtual Threads)
- PostgreSQL (доступный экземпляр)
- Google OAuth2 credentials (для аутентификации)

---

## 🚀 Установка и настройка

### 1. Клонирование репозитория


git clone https://github.com/your-org/opencasino-server.git
cd opencasino-server


### 2. Настройка базы данных

Создайте базу данных PostgreSQL и выполните SQL-схему:

```
CREATE TABLE IF NOT EXISTS players (
    id UUID PRIMARY KEY,
    username VARCHAR NOT NULL,
    balance NUMERIC(15,2),
    first_name VARCHAR NOT NULL,
    last_name VARCHAR NOT NULL,
    email VARCHAR NOT NULL,
    user_hash VARCHAR,
    created_at TIMESTAMP,
    last_modified TIMESTAMP,
    CONSTRAINT unique_user UNIQUE (username, email)
);
```


### 3. Переменные окружения

Сервер ожидает следующие переменные окружения для подключения к базе данных:

```
export DATABASE_HOST=localhost
export DATABASE_PORT=5432
export DATABASE_USER=your_user
export DATABASE_PASSWORD=your_password
export DATABASE_DB=opencasino
```

### 4. Настройка OAuth2

Создайте файл src/main/resources/auth.properties:

```
app.oauth2.clientId=YOUR_GOOGLE_CLIENT_ID
app.oauth2.clientSecret=YOUR_GOOGLE_CLIENT_SECRET
```

### 5. Настройка подключения к БД (альтернативный способ)

Создайте файл src/main/resources/database.properties:


```
postgres.host=localhost
postgres.port=5432
postgres.database=opencasino
postgres.user=your_user
postgres.password=your_password
```


---

## ▶️ Запуск

### Через Gradle Wrapper


# Linux / macOS
`./gradlew bootRun`

# Windows
`gradlew.bat bootRun`


### Сборка JAR


`./gradlew build
java -jar build/libs/openCasino_server-1.0.1.jar`


### Сборка Fat JAR (Shadow)


`./gradlew shadowJar
java -jar build/libs/openCasino_server-1.0.1-all.jar`


Сервер запустится на порту 8080 по умолчанию.

---

## 🔌 WebSocket API

### Подключение


`ws://localhost:8080/ws`


### Формат сообщения

Все сообщения передаются в формате JSON:


```
{
"type": <int>,
"serviceId": "<string | null>",
"data": <object | null>
}
```


| Поле | Описание |
|---|---|
| type | Числовой код типа сообщения |
| serviceId | Идентификатор игры: "Blackjack" или "Poker" |
| data | Полезная нагрузка (зависит от типа сообщения) |

### Типы сообщений

#### Общие

| Код | Константа | Направление | Описание |
|---|---|---|---|
| 1 | MESSAGE | S→C | Текстовое сообщение |
| 50 | AUTH_EVENT | C→S | Событие аутентификации |
| 40 | FAILURE | S→C | Ошибка |

#### Управление комнатами

| Код | Константа | Направление | Описание |
|---|---|---|---|
| 7 | GAME_ROOM_CREATE | C→S | Создание комнаты (Poker) |
| 10 | GAME_ROOM_JOIN | C→S | Запрос на присоединение к комнате |
| 11 | GAME_ROOM_JOIN_WAIT | S→C | Игрок в очереди ожидания |
| 12 | GAME_ROOM_JOIN_SUCCESS | S→C | Успешное присоединение + настройки |
| 44 | GAME_ROOM_JOIN_FAILURE | S→C | Ошибка присоединения |
|13 | GAME_START | S→C | Игра началась |
| 14 | GAME_ROOM_STATUS | S→C | Статус комнаты (результат раунда) |
| 20 | GAME_ROOM_START | S→C | Комната запущена |
| 21 | GAME_ROOM_CLOSE | S→C | Комната закрыта |

#### Игровой процесс

| Код | Константа | Направление | Описание |
|---|---|---|---|
| 77 | BET | C→S | Размещение ставки / Buy-in |
| 88 | BET_FAILURE | S→C | Ошибка ставки |
| 100 | UPDATE | S→C | Обновление состояния игры |
| 101 | INFO | C→S / S→C | Запрос/ответ информации об игроке |
| 200 | PLAYER_DECISION | C→S | Решение игрока (Hit/Stand/Raise/и т.д.) |

TODO(): Доделаю когда-нибудь