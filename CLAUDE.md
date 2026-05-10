# CLAUDE.md — openCasino_server

Context for Claude Code sessions. See `AGENTS.md` for general build/run instructions and codebase conventions; this file documents in-progress feature work and decisions specific to it.

## Active feature work

**Branch `feature/auth-spring-security`** — in progress.

Goal: expose this server as a public API platform with Spring Security WebFlux + JWT, multi-provider OAuth login, local registration, and persisted users (separate from `Players`).

GitLab issues (project: `oss/opencasino-server`):
- !2 — Auth phase 1: TLS + Spring Security baseline
- !3 — Auth phase 2: User/auth entity & persistence
- !4 — Auth phase 3: Local registration + email/password login
- !5 — Auth phase 4: JWT issuance + OAuth2 Resource Server
- !6 — Auth phase 5: WebSocket handshake auth + secure player binding
- !7 — Auth phase 6: Multi-provider OAuth2 (Google + 1–2 ещё)
- !8 — Auth phase 7: Refresh tokens + revocation
- !9 — Auth phase 8: CORS + rate limiting + audit logging

Each issue has its own DoD and detailed task list. Read the issue before starting a phase.

## Roadmap (dependency order)

Phases listed by dependency — don't merge a later phase before earlier ones are stable. Each phase = one merge request, branched off `feature/auth-spring-security`, merged back into it; the umbrella branch lands in `main` once the whole roadmap is ready (or in slices, by user decision).

1. **Phase 1 — TLS + Spring Security baseline** (!2). Wire `spring-boot-starter-security`, write reactive `SecurityWebFilterChain` (deny by default), enable `server.ssl.*` so tokens travel only over `wss://`.
2. **Phase 2 — User/auth entity** (!3). New tables `users` and `user_oauth_identities`. Drop unused `players.user_hash`; add `players.user_id` FK. New package `com.opencasino.server.user`.
3. **Phase 3 — Local registration + login** (!4). REST endpoints + BCrypt; stub JWT response (real JWT in phase 4).
4. **Phase 4 — JWT issuance + Resource Server** (!5). Recommended RS256 with rotating keys from env-mounted PEM. `oauth2ResourceServer().jwt()` validates incoming.
5. **Phase 5 — WebSocket handshake auth** (!6). Token in `Sec-WebSocket-Protocol` (preferred) or query string. Replace any `playerUUID`-from-client trust in `MainWebSocketHandler`, `BlackjackRoomServiceImpl`, `PokerRoomServiceImpl` — derive identity from `Principal` only.
6. **Phase 6 — Multi-provider OAuth2** (!7). `spring-security-oauth2-client` for Google (existing config to be assimilated), Yandex, GitHub. Account-takeover guard: never link unverified-email OAuth identity to existing local account.
7. **Phase 7 — Refresh tokens + revocation** (!8). `refresh_tokens` table with hash, rotation on use, replay detection.
8. **Phase 8 — Cross-cutting** (!9). CORS allowlist, per-token/IP rate limit (bucket4j), structured audit log for auth events.

## Out of scope (deferred / separate)

- Pot distribution in `PokerGameRoom` (unrelated, tracked in !1).
- API keys for service-to-service integrations (separate epic when third-party developers come).
- KYC / AML / gambling-license compliance (legal first; only after that — technical wiring).
- Email-verification flow (linked but deferred — `email_verified` flag exists from phase 2, the email-send pipeline is its own work).

## Conventions for this branch

- **Persistence is reactive R2DBC.** Do not reintroduce JPA/Hibernate. See AGENTS.md for the migration that already happened.
- **Auth code in `com.opencasino.server.security`** (new package). Keep it separate from game code.
- **Auth domain in `com.opencasino.server.user`** (new package). Distinct from `game.model.Players`.
- **No client-trusted identity.** After phase 5, treat any `playerUUID` / `userId` field in inbound WebSocket/REST messages as untrusted — always cross-check with `Principal`.
- **Tests:**
  - Unit: mock `R2dbcEntityTemplate`, `JwtDecoder`, etc.
  - Integration: use `r2dbc-h2` (already in test runtime). Schema migrations applied to H2 in test profile.
  - For Security: a test profile that disables auth where existing non-auth tests run (or supplies a stub `Principal`), so phase 1 doesn't break the existing 613 tests.
- **No comments** explaining what code does — only why-non-obvious. Don't restate the issue in code.
- **Don't touch game logic** (`game/poker`, `game/blackjack`) from auth changes. If a security check needs to live next to game logic, extract a thin auth-aware façade and keep `game/*` untouched.

## Key files affected (forward look)

- `build.gradle.kts` — uncomment `spring-boot-starter-security`, add `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-oauth2-client`. Maybe `bucket4j-spring-boot-starter` in phase 8.
- `config/ApplicationConfiguration.kt` — leave game-side config alone; add `SecurityConfig` in new `security` package instead of stuffing here.
- `config/OAuth2Config.kt` — current Google id_token verifier needs decision: assimilate into Spring OAuth2 client flow, or keep only for one specific use. Don't have both code paths active.
- `network/websocket/MainWebSocketHandler.kt`, `network/websocket/UserSessionWebSocketHandler.kt` — phase 5 rewires identity derivation.
- `service/impl/BlackjackRoomServiceImpl.kt`, `service/impl/PokerRoomServiceImpl.kt` — phase 5 stops trusting `initialData.playerUUID`. Each player session now resolves player via `Principal` → `users.id` → `players.user_id`.
- New: `security/SecurityConfig.kt`, `security/jwt/JwtIssuer.kt`, `user/User.kt`, `user/UserRepository.kt`, etc.
- `resources/db/` — schema migrations (additive). The current single `schema-postgresql.sql` will likely grow into per-phase files or migrate to Flyway/Liquibase if it gets unwieldy.

## Workflow notes for future sessions

- This umbrella branch should not stay in sync with `main` by accident — rebase only when phase 1 is mergeable.
- For each phase, create a sub-branch (`auth/phase-N-short-name`), open MR against `feature/auth-spring-security`, mention the issue (`Closes #N`).
- When closing a phase, update this file: strike-through the line in the roadmap and add a short note on what differed from the issue plan if anything.
