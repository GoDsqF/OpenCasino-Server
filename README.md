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
- [Docker / контейнеризация](#-docker--контейнеризация)
- [WebSocket API](#-websocket-api)
- [Статус и дорожная карта](#-статус-и-дорожная-карта)

---

## 🎯 Обзор проекта

OpenCasino Server — это реактивный игровой сервер, обеспечивающий:

- **Многопользовательские игры в реальном времени** через WebSocket
- **Blackjack** — классическая карточная игра против дилера, с поддержкой DOUBLE / SPLIT и мульти-руками в рамках одного раунда
- **Texas Hold'em Poker** — многопользовательский покер (2–6 игроков), Fixed/Pot/No Limit, полноценный SHOWDOWN с распределением side-pot-ов
- **Балансовая модель `balance_ledger`** — append-only журнал движений (`BLACKJACK_ROUND`, `POKER_BUY_IN`, `POKER_CASH_OUT`) + актуальный `users.balance` как denormalised cache. Отключение игрока посреди раунда → auto-stand/fold; покерный стек возвращается на баланс через `POKER_CASH_OUT`.
- **Аутентификация** — Spring Security WebFlux + RS256 JWT (`oauth2ResourceServer().jwt()`), локальная регистрация по `email + password + displayName`, BCrypt-хеши. Идентичность игроков и пользователей живёт в одной таблице `users` (Phase 5 фолданул легаси `players` в `users`). WebSocket handshake требует JWT в `Authorization` / `?token=` / `Sec-WebSocket-Protocol: bearer, <jwt>`.
- **Refresh-токены с ротацией и revocation** (Phase 7) — `POST /auth/refresh`, `POST /auth/logout`, replay-detection.
- **OAuth2-вход через Google** (Phase 6) — на выходе тот же RS256-JWT, что и у локального login. Yandex / GitHub отложены.
- **Реактивное взаимодействие с базой данных** PostgreSQL через R2DBC + Liquibase
- **Игровой цикл (game loop)** с настраиваемой частотой обновления

---

## 🏗 Архитектура

Проект построен по многослойной архитектуре:

1. Security Layer — Spring Security WebFlux + `oauth2ResourceServer().jwt()`, RS256 JWT issued by `JwtIssuer`, WebSocket handshake auth через `WebSocketBearerTokenAuthenticationConverter`, ротация refresh-токенов в `RefreshTokenService`
2. WebSocket Layer — `MainWebSocketHandler` (`/ws`, аутентифицированный), `UserSessionWebSocketHandler` (диспетчер по `serviceId`+`type`), `MenuWebSocketHandler` (`/ws/menu`, anonymous metadata), `WebSocketSessionService`
3. Service Layer — `BlackjackRoomService`, `PokerRoomService`, `AuthService`, `MenuService`, `BalanceLedgerService`
4. Game Logic Layer — `BlackjackGameRoom` / `PokerGameRoom`, `BlackjackPlayer` / `PokerPlayer`, `CardDeck`, `PokerHand` (showdown + side-pot)
5. Data Layer — `UserRepository`, `RefreshTokenRepository`, `BalanceLedgerRepository`, R2DBC, PostgreSQL, Liquibase migrations

### Ключевые паттерны:
- Factory Pattern — создание игроков (BlackjackPlayerFactory, PokerPlayerFactory)
- Observer/Publisher Pattern — рассылка обновлений через WebSocketMessagePublisher
- Game Loop Pattern — периодическое обновление состояния комнат через Scheduler
- Pack/DTO Pattern — разделение внутренней модели и сетевого представления (InitPack, UpdatePack, PrivateUpdatePack)

---

## 🎮 Основной функционал

### 🃏 Blackjack

- Игра один на один против дилера (`MAX_BLACKJACK_PLAYERS = 1`, multi-player — отдельный эпик)
- Колода из 8 стандартных колод (416 карт) с reshuffle при пробитии порога (`reshuffle-threshold`)
- Поддерживаемые решения игрока:
    - HIT — взять ещё одну карту
    - STAND — остановиться
    - DOUBLE — удвоить ставку (одна добор-карта, рука закрывается)
    - SPLIT — разделить парную руку на две, активная рука переключается через `activeHandIndex`
- Полноценный мульти-hand resolution: за раунд игрок может играть до нескольких рук (после SPLIT), каждая со своим итогом
- Автоматическая логика дилера (берёт карты до 17 очков)
- Условия победы по каждой руке: `PlayerWin`, `PlayerWinBlackjack`, `DealerWin`, `DealerBlackjack`, `Draw`
- Per-round запись в `balance_ledger` (`BLACKJACK_ROUND`, delta = totalPayout − totalBet) + UPDATE `users.balance` атомарно
- Disconnect посреди раунда → auto-stand активной руки, синхронный settle перед закрытием сессии
- Автоматический сброс стола и начало новой игры

### ♠️ Texas Hold'em Poker

- Многопользовательская игра (2–6 игроков)
- Система создания и присоединения к комнатам (`GAME_ROOM_CREATE` / `GAME_ROOM_JOIN` с `reconnectKey` = UUID комнаты)
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
- Buy-in через ledger: `POKER_BUY_IN` (delta=−buyIn) на вход за стол; per-round движения внутри игры — только в `PokerPlayer.stack`
- Cash-out на disconnect: остаток стека возвращается на баланс через `POKER_CASH_OUT` (delta=+stack), сначала auto-fold, затем atomic UPDATE+ledger через `BalanceLedgerService.applyDelta`
- Блайнды (малый/большой) с автоматической ротацией
- Полный SHOWDOWN с распределением side-pot-ов: `commitToStake` как единая точка списания, `PokerPlayer.totalContribution` — вход для side-pot, эмит `SHOWDOWN_RESULT` (type=102, payload `PokerShowdownPack`) с per-player payout и breakdown банков, hole-карты раскрываются при revealed showdown
- Оценка покерных комбинаций: Straight Flush, Four of a Kind, Full House, Flush, Straight, Three of a Kind, Two Pair, Pair, High Card
- Скрытие карт других игроков (каждый видит только свои карты — реализовано через `CardDeck.toPublicView()`)
- Валидация ставок

### 🌐 Сетевое взаимодействие

- WebSocket — двунаправленная связь в реальном времени
- Реактивная модель (Spring WebFlux + Project Reactor)
- Система сессий и управление подключениями
- Широковещательные (broadcast) и адресные сообщения
- Система очередей ожидания для подбора игроков
- JSON-сериализация через Gson

### 🔐 Аутентификация

- **Локальная регистрация**: `POST /auth/register` с `email`, `password` (≥8 chars), `displayName` (3..32 chars, `[A-Za-z0-9_-]`, денилист сабстрок через `app.auth.displayNameBlocklist`). BCrypt-хеш в `users.password_hash`.
- **Логин**: `POST /auth/login` → подписанный RS256 JWT (15-минутный access-token) + opaque refresh-token (32 байта энтропии, SHA-256 хранится в `refresh_tokens`).
- **Refresh**: `POST /auth/refresh` ротирует refresh, выдаёт новую пару `(access, refresh)`; replay старого после ротации → `REFRESH_REPLAY_DETECTED` и ревок всех refresh-токенов пользователя.
- **Logout**: `POST /auth/logout` — single-session revoke текущего refresh (idempotent, 204). Logout-all/sessions API — отложен в MR-9.
- **`GET /auth/me`** — identity (`userId`, `email`, `displayName`, `balance`, `roles`) из текущего JWT.
- **OAuth2 (Google)**: `GET /oauth2/authorization/google` → провайдер → callback `/login/oauth2/code/google` → 302 на `app.auth.oauth2.success-redirect` с `?token=<jwt>&refreshToken=…&expiresAt=…&refreshExpiresAt=…`. На выходе тот же RS256-JWT, что и у локального login. Yandex / GitHub — отложены.
- **WebSocket handshake**: JWT принимается в `Authorization: Bearer …`, `?token=…`, или sub-protocol `Sec-WebSocket-Protocol: bearer, <jwt>` (K8s-style — сервер эхит только `bearer`).
- **Identity**: `Principal.getName()` = `users.id` (UUID). После Phase 5 любой `playerUUID` во входящих WS-сообщениях игнорируется — кросс-чек идёт только с `Principal`.

Конфигурация: `app.jwt.*` (issuer, accessTtl, refreshTtl, kid, PEM-ключи) + `app.auth.displayNameBlocklist` + `spring.security.oauth2.client.registration.google.*` + `app.auth.oauth2.success-redirect` / `failure-redirect`. См. `src/main/resources/auth.properties.example`.

Полная REST-спецификация (request/response shapes, error codes, sequence flow) — в [`opencasino-docs/api/rest-auth.md`](https://gitlab.godsq.ru/oss/opencasino-docs/-/blob/main/api/rest-auth.md).

### 📊 Главное меню

- Anonymous WebSocket-канал `/ws/menu` (`permitAll`, без JWT) — отдаёт metadata о доступных играх и количестве активных игроков. Реализован в `MenuWebSocketHandler` + `MenuService`.
- Игровой `/ws` требует JWT и используется только для входа в комнату/игрового цикла.

---

## 🛠 Технологический стек

| Технология | Назначение |
|---|---|
| Kotlin 1.9.25 | Основной язык разработки |
| Java 21 | Целевая платформа (Virtual Threads) |
| Spring Boot 3.4.1 | Фреймворк приложения |
| Spring WebFlux | Реактивный веб-сервер |
| Spring Security + OAuth2 Resource Server + OAuth2 Client | Auth, RS256 JWT, OAuth2-login |
| Project Reactor / Kotlin Coroutines (Reactor extensions) | Реактивные потоки |
| R2DBC (PostgreSQL) | Реактивный доступ к БД |
| Liquibase | Схема и миграции |
| PostgreSQL 16 | СУБД (prod); H2 (`r2dbc-h2`) — integration-тесты |
| Gson | JSON-сериализация WS-протокола |
| Jackson | REST-сериализация (`auth/*`) |
| Gradle 8.11.1 | Система сборки |
| Spring Boot Gradle plugin (`bootJar`) | Сборка исполняемого JAR |

---

## 📦 Требования

- JDK 21+ (требуется поддержка Virtual Threads)
- PostgreSQL (доступный экземпляр) — на старте Liquibase прокатывает миграции
- TLS-сертификаты + RS256 keypair для JWT (см. §4 ниже; для dev можно сгенерировать локально)
- (Опционально) Google OAuth2 credentials — без них `/oauth2/authorization/google` просто отдаст 404, остальное приложение работает

---

## 🚀 Установка и настройка

### 1. Клонирование репозитория


git clone https://github.com/GoDsqF/openCasino_server.git
cd openCasino_server


### 2. Настройка базы данных

Схема управляется **Liquibase** (`spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml`). Миграции лежат в `src/main/resources/db/changelog/changes/` и применяются автоматически на старте приложения. Достаточно создать пустую PostgreSQL-базу — Liquibase подтянет:

| Миграция | Что делает |
|---|---|
| `001-baseline-players.sql` | Исторический baseline (легаси `players`) |
| `002-users-and-oauth-identities.sql` | `users`, `user_oauth_identities` |
| `003-merge-players-into-users.sql` | Фолдает легаси `players` в `users` |
| `004-email-verified-default-true.sql` | `email_verified` → `true` by default (Phase 6) |
| `005-refresh-tokens.sql` | Таблица `refresh_tokens` (Phase 7) |
| `006-balance-ledger.sql` | Append-only `balance_ledger` (MR-5) |

JDBC-DataSource для Liquibase выводится из `spring.r2dbc.url` через `LiquibaseDataSourceConfig` — отдельные `spring.liquibase.url/username/password` указывать не надо (hotfix `!16`).

Актуальный shape таблицы `users` (после миграции `003`+`004`):

```sql
CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    password_hash   VARCHAR(255),
    role            VARCHAR(32)  NOT NULL DEFAULT 'USER',
    balance         DOUBLE PRECISION NOT NULL DEFAULT 0,
    display_name    VARCHAR(64)  NOT NULL,
    first_name      VARCHAR(255),
    last_name       VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at   TIMESTAMP WITH TIME ZONE
);
-- + user_oauth_identities (provider, subject) FK users(id)
-- + refresh_tokens (id, user_id, token_hash UNIQUE, created_at, expires_at, revoked_at, user_agent, ip) FK users(id)
-- + balance_ledger (id, user_id, round_id, delta, reason VARCHAR(32), created_at) FK users(id)
```

Легаси-таблица `players` была фолданута в `users` (Phase 5, миграция `003-merge-players-into-users.sql`). Поля `user_agent` / `ip` в `refresh_tokens` сейчас пишутся `NULL` — заполнятся в Phase 8.

### 3. Переменные окружения

Подключение к БД конфигурируется через `spring.r2dbc.*` в `src/main/resources/database.properties` (он gitignored — заведите локально). Хосты/учётки оттуда подставляются плейсхолдерами из ENV:

```
export DATABASE_HOST=localhost
export DATABASE_PORT=5432
export DATABASE_USER=your_user
export DATABASE_PASSWORD=your_password
export DATABASE_DB=opencasino
```

Spring Boot автоматически собирает `ConnectionFactory` из этих свойств — отдельного `@Bean` в коде нет.

### 4. Настройка auth (JWT + display-name policy)

Создайте `src/main/resources/auth.properties` по шаблону `auth.properties.example`. Обязательное:

```
# RS256 keypair (см. auth.properties.example — есть команды openssl)
app.jwt.privateKeyPem=...   # или env APP_JWT_PRIVATE_KEY_PEM
app.jwt.publicKeyPem=...    # или env APP_JWT_PUBLIC_KEY_PEM
app.jwt.issuer=opencasino
app.jwt.accessTtl=PT15M
app.jwt.refreshTtl=P30D
app.jwt.keyId=default

# Денилист сабстрок для displayName при регистрации (case-insensitive)
app.auth.displayNameBlocklist=admin,root,system,support,moderator

# OAuth2 client (Phase 6 — Google)
spring.security.oauth2.client.registration.google.client-id=
spring.security.oauth2.client.registration.google.client-secret=
spring.security.oauth2.client.registration.google.scope=openid,email,profile
app.auth.oauth2.success-redirect=https://<your-frontend>/auth/callback
app.auth.oauth2.failure-redirect=https://<your-frontend>/auth/callback
```

> **Короткие env-vars.** Файлы под `src/main/resources/` нужны только для локальной разработки. В контейнере приложение умеет стартовать без mounted `/config/*.properties`: `ShortEnvAliasPostProcessor` мапит `GOOGLE_OAUTH_CLIENT_ID/CLIENT_SECRET/SCOPE` → `spring.security.oauth2.client.registration.google.*` и `OAUTH_SUCCESS_REDIRECT`/`OAUTH_FAILURE_REDIRECT` → `app.auth.oauth2.*-redirect`, плюс закладывает дефолты для `app.jwt.*`, `server.ssl.enabled`, `app.auth.displayNameBlocklist`. Любой mounted property-файл или env с каноническим именем имеет приоритет (alias добавлен как `addLast`).

Сгенерировать ключи:
```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
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


### Сборка исполняемого JAR

```
./gradlew bootJar
java -jar build/libs/openCasino_server-1.0.3.jar
```

`bootJar` — стандартная задача Spring Boot Gradle-плагина: артефакт самодостаточен, содержит вложенные зависимости и корректный `Main-Class` (Spring Boot loader). Шейдинг не используется.

Сервер запустится на порту 8080 по умолчанию.

---

## 🐳 Docker / контейнеризация

В корне репозитория лежат `Dockerfile` (multi-stage build) и `.dockerignore`. Образ рассчитан на запуск **без изменений** в plain Docker, Docker Compose, GitLab CI/CD и Kubernetes. Секреты (пароль БД, OAuth client secret, token secret) и TLS-сертификаты **никогда не попадают в образ** — они инъектятся в рантайме.

### Сборка

```bash
docker build -t opencasino-server:1.0.3 .
```

Pre-build артефакты не нужны — стадия `builder` сама запускает `./gradlew bootJar` и копирует получившийся Spring Boot JAR в рантайм-образ.

### Build-time аргументы (`--build-arg`)

| ARG | По умолчанию | Назначение |
|---|---|---|
| `JDK_IMAGE` | `eclipse-temurin:21-jdk-jammy` | Образ компилятора. Поменяйте на корпоративное зеркало в air-gapped CI. |
| `JRE_IMAGE` | `eclipse-temurin:21-jre-jammy` | Базовый рантайм. Jammy (glibc) выбран вместо Alpine ради совместимости с Netty / r2dbc-postgresql. |
| `APP_USER` | `opencasino` | Имя non-root пользователя в контейнере. |
| `APP_UID` / `APP_GID` | `10001` / `10001` | Пиннуем числовые идентификаторы — чтобы `securityContext.runAsUser` в k8s и host bind-mounts вели себя предсказуемо. |
| `APP_HOME` | `/opt/app` | Куда установлен `app.jar`. |
| `GRADLE_OPTS` | пусто | Прокидывается в Gradle (proxy, memory) для CI-сборок. |

### Runtime переменные окружения

| ENV | По умолчанию | Назначение |
|---|---|---|
| `SERVER_PORT` | `8080` | Spring `server.port`. Используется в `EXPOSE` и HEALTHCHECK. |
| `SPRING_PROFILES_ACTIVE` | пусто | Активные профили Spring (например `prod,ssl`). |
| `JAVA_OPTS` | пусто | Дополнительные JVM-флаги, добавляемые к команде запуска. |
| `JAVA_TOOL_OPTIONS` | `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom` | Подхватывается JVM автоматически — сайзинг по cgroup-лимитам и быстрый источник энтропии для TLS. |
| `TZ` | `UTC` | Тайм-зона контейнера (важна для логов game loop). |
| `DATABASE_HOST` / `_PORT` / `_USER` / `_PASSWORD` / `_DB` | — | R2DBC + Liquibase. Если `/config/database.properties` не смонтирован, эти env-vars достаточны. |
| `APP_JWT_ISSUER`, `APP_JWT_ACCESS_TTL`, `APP_JWT_REFRESH_TTL`, `APP_JWT_KEY_ID`, `APP_JWT_PRIVATE_KEY_PATH`, `APP_JWT_PUBLIC_KEY_PATH` | `opencasino`, `PT15M`, `P30D`, `default`, `/certs/jwt-private.pem`, `/certs/jwt-public.pem` | JWT RS256 + refresh-token TTL. Inline-варианты PEM — `APP_JWT_PRIVATE_KEY_PEM` / `APP_JWT_PUBLIC_KEY_PEM`. |
| `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`, `GOOGLE_OAUTH_SCOPE` | — / — / `openid,email,profile` | Phase 6. Мапятся на `spring.security.oauth2.client.registration.google.*` через `ShortEnvAliasPostProcessor`. Пустой `CLIENT_ID` или `CLIENT_SECRET` → флоу не регистрируется, `/oauth2/authorization/google` отдаёт 404. |
| `OAUTH_SUCCESS_REDIRECT`, `OAUTH_FAILURE_REDIRECT` | — | URI редиректа после OAuth-login/failure. Должен указывать на существующий SPA-роут (текущий фронт — `/auth/callback`). |
| `APP_AUTH_DISPLAY_NAME_BLOCKLIST` | `admin,root,system,support,moderator` | Денилист сабстрок для `displayName` при регистрации. |
| `SERVER_SSL_ENABLED`, `SERVER_SSL_CERTIFICATE`, `SERVER_SSL_CERTIFICATE_PRIVATE_KEY`, `SERVER_SSL_TRUST_CERTIFICATE` | `false` / `/certs/cert.pem` / `/certs/privkey.pem` / `/certs/chain.pem` | Включение TLS и пути к PEM-файлам. С дефолтными путями достаточно одного `SERVER_SSL_ENABLED=true`. |

### Точки монтирования (Volumes)

- **`/certs`** — TLS + JWT-ключи (`cert.pem`, `privkey.pem`, `chain.pem`, `jwt-private.pem`, `jwt-public.pem`). С дефолтными путями достаточно `SERVER_SSL_ENABLED=true`.
- **`/config`** — drop-in каталог. `application.properties` уже импортит `optional:file:/config/{application,database,auth,ssl}.properties` — никаких флагов вроде `--spring.config.additional-location` передавать не надо. **Опциональный**: для секретов и многострочных PEM удобнее, но при наличии env-vars и `/certs/*.pem` можно вообще не монтировать.

### Reverse-proxy (nginx) и OAuth

Backend должен получать три префикса напрямую:

```nginx
location /auth/                       { proxy_pass http://opencasino-server:8080; }
location /oauth2/authorization/       { proxy_pass http://opencasino-server:8080; }   # OAuth start
location /login/oauth2/               { proxy_pass http://opencasino-server:8080; }   # OAuth callback от провайдера (Spring зашивает /login/oauth2/code/{registration})
```

`OAUTH_SUCCESS_REDIRECT` (например `/auth/callback`) — это уже SPA-роут; оставьте его SPA-fallback'у (`try_files $uri /index.html`). Не используйте `location /oauth2/` целиком, иначе SPA-фолбэки под `/oauth2/*` начнут уходить на backend. В **Google Cloud Console** в Authorized redirect URIs указывайте *callback сервера* — `https://<host>/login/oauth2/code/google`, не SPA-success-redirect.

### Открытые порты

| Порт | Назначение |
|---|---|
| `8080` | HTTP / WebSocket (по умолчанию) |
| `8443` | HTTPS / WSS, когда включён SSL |

`EXPOSE` — это документация. Что публиковать наружу — решает оркестратор.

### Безопасность / права

- Процесс запускается под `opencasino` (uid `10001`), shell `/sbin/nologin`. `app.jar`, `/certs`, `/config` принадлежат этому uid.
- PID 1 — `tini -g`: корректно форвардит SIGTERM в JVM, Spring штатно завершается при `docker stop` / `kubectl delete pod`.
- В рантайм-образе нет JDK / Gradle / shell-утилит сборки — только `tini`, `ca-certificates`, `curl` (для HEALTHCHECK).
- Файловую систему можно ставить read-only (`readOnlyRootFilesystem: true` в k8s) — приложение пишет только в смонтированные тома.

### Запуск в plain Docker

```bash
docker run --rm --name opencasino \
  -p 8080:8080 \
  -e DATABASE_HOST=db.internal -e DATABASE_PORT=5432 \
  -e DATABASE_USER=pgadmin -e DATABASE_PASSWORD='***' -e DATABASE_DB=casino \
  -e GOOGLE_OAUTH_CLIENT_ID='***.apps.googleusercontent.com' \
  -e GOOGLE_OAUTH_CLIENT_SECRET='***' \
  -e OAUTH_SUCCESS_REDIRECT='https://opencasino.example.com/auth/callback' \
  -e OAUTH_FAILURE_REDIRECT='https://opencasino.example.com/auth/callback' \
  -v "$PWD/certs:/certs:ro" \
  opencasino-server:1.0.3
```

`ShortEnvAliasPostProcessor` (`src/main/kotlin/com/opencasino/server/config/ShortEnvAliasPostProcessor.kt`, регистрируется через `META-INF/spring.factories`) разворачивает `GOOGLE_OAUTH_*` в канонические `spring.security.oauth2.client.registration.google.*` и проставляет дефолты для `app.jwt.*` — поэтому `/config/auth.properties` монтировать не нужно. JWT-ключи всё равно нужны на `/certs/jwt-{private,public}.pem` либо inline через `APP_JWT_PRIVATE_KEY_PEM` / `APP_JWT_PUBLIC_KEY_PEM`.

Включить TLS:

```bash
docker run --rm -p 8443:8443 \
  -e SERVER_PORT=8443 \
  -e SERVER_SSL_ENABLED=true \
  -e SERVER_SSL_CERTIFICATE=/certs/cert.pem \
  -e SERVER_SSL_CERTIFICATE_PRIVATE_KEY=/certs/privkey.pem \
  -e SERVER_SSL_TRUST_CERTIFICATE=/certs/chain.pem \
  -v /etc/letsencrypt/live/fndry.ddns.net:/certs:ro \
  ... \
  opencasino-server:1.0.3
```

### Docker Compose

```yaml
services:
  opencasino:
    image: opencasino-server:1.0.3
    build: .
    ports:
      - "8080:8080"
    env_file:
      - .env.opencasino     # DATABASE_*, GOOGLE_OAUTH_*, OAUTH_*_REDIRECT
    volumes:
      - ./certs:/certs:ro
    restart: unless-stopped
    depends_on:
      - postgres
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: pgadmin
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
      POSTGRES_DB: casino
    volumes:
      - pgdata:/var/lib/postgresql/data
volumes:
  pgdata:
```

### GitLab CI/CD

```yaml
build_image:
  stage: build
  image: docker:27
  services: [docker:27-dind]
  variables:
    IMAGE: $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
  before_script:
    - echo "$CI_REGISTRY_PASSWORD" | docker login -u "$CI_REGISTRY_USER" --password-stdin "$CI_REGISTRY"
  script:
    - docker buildx build --pull --tag "$IMAGE" --tag "$CI_REGISTRY_IMAGE:latest" .
    - docker push "$IMAGE"
    - docker push "$CI_REGISTRY_IMAGE:latest"
```

Секреты (`DATABASE_PASSWORD`, `GOOGLE_OAUTH_CLIENT_SECRET`, JWT private key) храните как **masked + protected** CI/CD variables. PEM-ключи удобнее как `File`-type variables — runner разворачивает их в файл, который mount'ится как `/certs/jwt-private.pem`.

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: opencasino-server
spec:
  replicas: 2
  selector:
    matchLabels: { app: opencasino-server }
  template:
    metadata:
      labels: { app: opencasino-server }
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        fsGroup: 10001
      containers:
        - name: app
          image: registry.example.com/opencasino-server:1.0.3
          ports:
            - { name: http,  containerPort: 8080 }
            - { name: https, containerPort: 8443 }
          envFrom:
            - secretRef: { name: opencasino-db }       # DATABASE_*
            - secretRef: { name: opencasino-auth }     # GOOGLE_OAUTH_*, OAUTH_*_REDIRECT
          env:
            - { name: SPRING_PROFILES_ACTIVE, value: "prod" }
          volumeMounts:
            - { name: certs, mountPath: /certs, readOnly: true }
          livenessProbe:
            tcpSocket: { port: http }
            initialDelaySeconds: 30
          readinessProbe:
            tcpSocket: { port: http }
            periodSeconds: 5
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities: { drop: ["ALL"] }
      volumes:
        - name: certs
          secret:
            secretName: opencasino-tls           # cert.pem / privkey.pem / chain.pem
            defaultMode: 0400
```

`HEALTHCHECK` из Dockerfile в Kubernetes игнорируется — оркестратор использует свои `livenessProbe` / `readinessProbe`. Это ожидаемо.

---

## 🔌 WebSocket API

### Подключение

```
ws://localhost:8080/ws        # dev — игровой канал (требует JWT)
wss://<host>/ws               # TLS — игровой канал (требует JWT)
ws://localhost:8080/ws/menu   # anonymous metadata-канал (permitAll)
```

**Handshake требует JWT** — токен можно положить в любое из трёх мест (см. [`opencasino-docs/api/README.md §1.1`](https://gitlab.godsq.ru/oss/opencasino-docs/-/blob/main/api/README.md)):

| Транспорт | Пример |
|---|---|
| `Authorization` header | `Authorization: Bearer <jwt>` |
| Query string | `ws://host/ws?token=<jwt>` |
| Sub-protocol (browser) | `new WebSocket('ws://host/ws', ['bearer', token])` |

Без валидного токена handshake фейлится с `HTTP 401` до открытия WS-соединения.


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
| 1 | MESSAGE | S→C | Текстовое сообщение (`GameMessagePack` или `string`) |
| 40 | FAILURE | S→C | Ошибка (см. `FailurePack`) |
| 50 | AUTH_EVENT | — | Декларирован, не обрабатывается (см. opencasino-docs §6) |
| 51 | GAME_LIST_UPDATE | — | Декларирован, не эмитится |

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
| 77 | BET | C→S | Размещение ставки (BJ) / Buy-in (Poker) |
| 88 | BET_FAILURE | S→C | Ошибка ставки / buy-in (`INVALID_BET`, `BET_BELOW_MIN`, `INSUFFICIENT_FUNDS`) |
| 100 | UPDATE | S→C | Снапшот стола (`BlackjackGameUpdatePack` / `PokerGameUpdatePack`) |
| 101 | INFO | C↔S | Запрос/ответ информации об игроке |
| 102 | SHOWDOWN_RESULT | S→C | **Poker.** `PokerShowdownPack` — per-player payout, side-pot breakdown, revealed hole-карты |
| 200 | PLAYER_DECISION | C→S | Решение игрока (Hit/Stand/Double/Split/Check/Call/Raise/Fold/All-in) |

> Полный каталог сообщений, payload-ы, TS-схемы и sequence-диаграммы потоков лежат в отдельном репозитории документации: <https://gitlab.godsq.ru/oss/opencasino-docs>. Этот раздел — короткая сводка для контрибьюторов сервера; источник правды для фронтенда — `opencasino-docs/api/`.

---

## 🗺 Статус и дорожная карта

| Область | Статус |
|---|---|
| Игровая логика Blackjack (HIT/STAND/DOUBLE/SPLIT, multi-hand) | готово (MR-3) |
| Игровая логика Texas Hold'em (showdown + side-pot distribution) | готово (MR-4) |
| Balance ledger (BJ per-round, Poker buy-in/cash-out) | готово (MR-5) |
| Реактивная персистентность (R2DBC + Liquibase) | внедрена |
| Docker-образ + GitLab CI | готовы (`build` / `package` / `verify`); `deploy`-стадия — заглушка под docker compose (single-host через SSH) |
| Spring Security + RS256 JWT | готово (Phase 4) |
| WebSocket handshake auth + `/ws/menu` (anonymous metadata) | готово (Phase 5 + MR-2) |
| Player↔User merge (legacy `players` → `users`) | готово (Phase 5) |
| Multi-provider OAuth | Google — готово (Phase 6); Yandex / GitHub отложены |
| Refresh tokens + ротация + revocation + replay-detection | готово (Phase 7) |
| CORS / rate-limit / `refresh_tokens.user_agent+ip` / audit log | запланировано (Phase 8) |
| Reconnect / resume посреди раунда | запланировано (MR-6) |
| Heartbeat ping/pong, protocol cleanup, sessions API, CI codegen | запланировано (MR-7…MR-10) |

Подробности по фазам auth-работ — в `CLAUDE.md` (локальный, не в git). Задачи трекаются в GitLab `oss/opencasino-server` (issues #2–#9). Документация API/протокола (включая полные payload-ы, sequence-flows и TS-схемы) живёт в отдельном репозитории <https://gitlab.godsq.ru/oss/opencasino-docs>.