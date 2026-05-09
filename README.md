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

## 🐳 Docker / контейнеризация

В корне репозитория лежат `Dockerfile` (multi-stage build) и `.dockerignore`. Образ рассчитан на запуск **без изменений** в plain Docker, Docker Compose, GitLab CI/CD и Kubernetes. Секреты (пароль БД, OAuth client secret, token secret) и TLS-сертификаты **никогда не попадают в образ** — они инъектятся в рантайме.

### Сборка

```bash
docker build -t opencasino-server:1.0.1 .
```

Pre-build артефакты не нужны — стадия `builder` сама запускает `./gradlew shadowJar` и собирает fat-JAR.

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
| `DATABASE_HOST` / `_PORT` / `_USER` / `_PASSWORD` / `_DB` | — | Уже читаются из `database.properties`. |
| `APP_AUTH_TOKENSECRET`, `APP_OAUTH2_CLIENTID`, `APP_OAUTH2_CLIENTSECRET` | — | OAuth2 / JWT секреты. Spring relaxed binding автоматически мапит их на `app.auth.tokenSecret` и т.п. |
| `SERVER_SSL_ENABLED`, `SERVER_SSL_CERTIFICATE`, `SERVER_SSL_CERTIFICATE_PRIVATE_KEY`, `SERVER_SSL_TRUST_CERTIFICATE` | — | Включение TLS и пути к PEM-файлам внутри контейнера (обычно `/certs/...`). |

### Точки монтирования (Volumes)

- **`/certs`** — TLS-материал (`cert.pem`, `privkey.pem`, `chain.pem`). Пути к ним передавайте через `SERVER_SSL_*` переменные или через `ssl.properties`.
- **`/config`** — drop-in каталог для дополнительных Spring property-файлов. Активируется аргументом `--spring.config.additional-location=file:/config/`, который можно передать как `CMD`.

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
  -e APP_AUTH_TOKENSECRET='***' \
  -e APP_OAUTH2_CLIENTID='***.apps.googleusercontent.com' \
  -e APP_OAUTH2_CLIENTSECRET='***' \
  -v "$PWD/certs:/certs:ro" \
  opencasino-server:1.0.1
```

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
      - .env.opencasino     # DATABASE_*, APP_AUTH_TOKENSECRET, APP_OAUTH2_*
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

Секреты (`DATABASE_PASSWORD`, `APP_AUTH_TOKENSECRET`, `APP_OAUTH2_CLIENTSECRET`) храните как **masked + protected** CI/CD variables. Сертификаты — как `File`-type variables, они монтируются в job рабочей директорией.

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
            - secretRef: { name: opencasino-auth }     # APP_AUTH_*, APP_OAUTH2_*
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