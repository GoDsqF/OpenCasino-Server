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
- **Blackjack** — классическая карточная игра против дилера
- **Texas Hold'em Poker** — многопользовательский покер с поддержкой различных типов ставок (Fixed Limit, Pot Limit, No Limit)
- **Аутентификация** — Spring Security WebFlux + RS256 JWT (`oauth2ResourceServer().jwt()`), локальная регистрация по `email + password + displayName`, BCrypt-хеши. Идентичность игроков и пользователей живёт в одной таблице `users` (Phase 5 фолданул легаси `players` в `users`). WebSocket handshake требует JWT в `Authorization` / `?token=` / `Sec-WebSocket-Protocol: bearer, <jwt>`. Multi-provider OAuth — следующая фаза.
- **Реактивное взаимодействие с базой данных** PostgreSQL через R2DBC + Liquibase
- **Игровой цикл (game loop)** с настраиваемой частотой обновления

---

## 🏗 Архитектура

Проект построен по многослойной архитектуре:

1. Security Layer - Spring Security WebFlux + `oauth2ResourceServer().jwt()`, RS256 JWT issued by `JwtIssuer`, WebSocket handshake auth через `WebSocketBearerTokenAuthenticationConverter`
2. WebSocket Layer - MainWebSocketHandler, UserSessionWebSocketHandler, WebSocketSessionService
3. Service Layer - RoomService, AuthService
4. Game Logic Layer - GameRoom, Player, CardDeck, PokerHand
5. Data Layer - UserRepository, R2DBC, PostgreSQL, Liquibase migrations

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

- **Локальная регистрация**: `POST /auth/register` с `email`, `password` (≥8 chars), `displayName` (3..32 chars, `[A-Za-z0-9_-]`, денилист сабстрок через `app.auth.displayNameBlocklist`). BCrypt-хеш в `users.password_hash`.
- **Логин**: `POST /auth/login` → подписанный RS256 JWT (15-минутный access-token + stub refresh, реальный refresh-flow в Phase 7).
- **`GET /auth/me`** для фронтенда (получить identity из текущего токена).
- **WebSocket handshake**: JWT принимается в `Authorization: Bearer …`, `?token=…`, или sub-protocol `Sec-WebSocket-Protocol: bearer, <jwt>` (K8s-style — сервер эхит только `bearer`).
- **Identity**: `Principal.getName()` = `users.id` (UUID). После Phase 5 любой `playerUUID` во входящих WS-сообщениях игнорируется.
- **Multi-provider OAuth** (Google / Yandex / GitHub) — следующая фаза (`#7`).

Конфигурация: `app.jwt.*` (issuer, accessTtl, kid, PEM-ключи) + `app.auth.displayNameBlocklist`. См. `src/main/resources/auth.properties.example`.

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
| Spring Boot Gradle plugin (`bootJar`) | Сборка исполняемого JAR |

---

## 📦 Требования

- JDK 21+ (требуется поддержка Virtual Threads)
- PostgreSQL (доступный экземпляр)
- Google OAuth2 credentials (для аутентификации)

---

## 🚀 Установка и настройка

### 1. Клонирование репозитория


git clone https://github.com/GoDsqF/openCasino_server.git
cd openCasino_server


### 2. Настройка базы данных

Схема управляется **Liquibase** (`spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml`). Миграции лежат в `src/main/resources/db/changelog/changes/` и применяются автоматически на старте приложения. Достаточно создать пустую PostgreSQL-базу — Liquibase подтянет `001-baseline-players.sql` … `003-merge-players-into-users.sql`.

Актуальный shape таблицы `users` (после миграции `003`):

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
```

Легаси-таблица `players` была фолданута в `users` (Phase 5, миграция `003-merge-players-into-users.sql`).

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
java -jar build/libs/openCasino_server-1.0.1.jar
```

`bootJar` — стандартная задача Spring Boot Gradle-плагина: артефакт самодостаточен, содержит вложенные зависимости и корректный `Main-Class` (Spring Boot loader). Шейдинг не используется.

Сервер запустится на порту 8080 по умолчанию.

---

## 🐳 Docker / контейнеризация

В корне репозитория лежат `Dockerfile` (multi-stage build) и `.dockerignore`. Образ рассчитан на запуск **без изменений** в plain Docker, Docker Compose, GitLab CI/CD и Kubernetes. Секреты (пароль БД, OAuth client secret, token secret) и TLS-сертификаты **никогда не попадают в образ** — они инъектятся в рантайме.

### Сборка

```bash
docker build -t opencasino-server:1.0.1 .
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
| `APP_JWT_ISSUER`, `APP_JWT_ACCESS_TTL`, `APP_JWT_KEY_ID`, `APP_JWT_PRIVATE_KEY_PATH`, `APP_JWT_PUBLIC_KEY_PATH` | `opencasino`, `PT15M`, `default`, `/certs/jwt-private.pem`, `/certs/jwt-public.pem` | JWT RS256. Inline-варианты — `APP_JWT_PRIVATE_KEY_PEM` / `APP_JWT_PUBLIC_KEY_PEM`. |
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
  opencasino-server:1.0.1
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
  opencasino-server:1.0.1
```

### Docker Compose

```yaml
services:
  opencasino:
    image: opencasino-server:1.0.1
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
          image: registry.example.com/opencasino-server:1.0.1
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
ws://localhost:8080/ws        # dev
wss://<host>/ws               # TLS
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
| 1 | MESSAGE | S→C | Текстовое сообщение |
| 40 | FAILURE | S→C | Ошибка (см. `FailurePack`) |

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

> Полный каталог сообщений, payload-ы, TS-схемы и sequence-диаграммы потоков лежат в отдельном репозитории документации: <https://gitlab.godsq.ru/oss/opencasino-docs>. Этот раздел — короткая сводка для контрибьюторов сервера; источник правды для фронтенда — `opencasino-docs/api/`.

---

## 🗺 Статус и дорожная карта

| Область | Статус |
|---|---|
| Игровая логика Blackjack | стабильна |
| Игровая логика Texas Hold'em | основной флоу работает; распределение pot-ов вынесено в issue !1 |
| Реактивная персистентность (R2DBC) | внедрена |
| Docker-образ + GitLab CI | готовы (`build` / `package` / `verify`); `deploy`-стадия — заглушка под docker compose |
| Spring Security + RS256 JWT | готово (Phase 4 — `!8`) |
| WebSocket handshake auth | готово (Phase 5 — `!9`) |
| Player↔User merge (legacy `players` → `users`) | готово (Phase 5 — `!9`) |
| Multi-provider OAuth | Google — готово (Phase 6 — `!7`); Yandex / GitHub отложены |
| Refresh tokens + revocation | запланировано (Phase 7 — `#8`) |
| CORS / rate-limit / audit log | запланировано (Phase 8 — `#9`) |

Подробности по фазам auth-работ — в `CLAUDE.md` (локальный, не в git). Задачи трекаются в GitLab `oss/opencasino-server` (issues #2–#9). Документация API/протокола живёт в отдельном репозитории <https://gitlab.godsq.ru/oss/opencasino-docs>.