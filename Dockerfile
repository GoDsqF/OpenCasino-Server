# syntax=docker/dockerfile:1.7
# ============================================================================
# openCasino_server — multi-stage image
# ----------------------------------------------------------------------------
# Designed to run identically under: plain `docker run`, Docker Compose,
# GitLab CI/CD jobs, and Kubernetes (Deployment + Secret/ConfigMap mounts).
#
# Secrets (DB password, OAuth client secret, token secret) and TLS material
# are NEVER baked into the image. They are injected at runtime via:
#   - environment variables (preferred for non-file values)
#   - a bind/volume/secret mount at /certs (TLS PEM files)
#   - a bind/volume/secret mount at /config (extra Spring property files)
# ============================================================================


# ----------------------------------------------------------------------------
# Build-time arguments. Override with: --build-arg NAME=value
# ----------------------------------------------------------------------------
# JDK_IMAGE   — image used to compile + assemble the executable Spring Boot JAR
# JRE_IMAGE   — slim runtime base (no compiler, smaller surface)
# APP_USER    — non-root username created in the runtime image
# APP_UID/GID — numeric IDs; pinned so K8s `runAsUser` / `fsGroup` line up
# APP_HOME    — install directory of the application
# GRADLE_OPTS — passed to Gradle during build (e.g. proxy settings in CI)
ARG JDK_IMAGE=eclipse-temurin:21-jdk-jammy
ARG JRE_IMAGE=eclipse-temurin:21-jre-jammy
ARG APP_USER=opencasino
ARG APP_UID=10001
ARG APP_GID=10001
ARG APP_HOME=/opt/app


# ============================================================================
# Stage 1 — builder: compiles Kotlin sources and produces the fat JAR
# ============================================================================
FROM ${JDK_IMAGE} AS builder

ARG GRADLE_OPTS=""
ENV GRADLE_OPTS=${GRADLE_OPTS} \
    GRADLE_USER_HOME=/root/.gradle

WORKDIR /workspace

# Copy wrapper + build scripts first so the dependency layer caches well.
# A change in src/ will not invalidate the dependency download layer.
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

RUN chmod +x ./gradlew \
 && ./gradlew --no-daemon --version

# Pre-fetch dependencies into the Gradle cache. `|| true` keeps the layer
# usable when a transient task graph fails — real failures surface in bootJar.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies -q || true

COPY src ./src

# Build the Spring Boot executable JAR. Tests are skipped on purpose: CI runs
# them in a dedicated job; the image build should not duplicate work.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test

# Normalize the artifact name so the runtime stage doesn't depend on version.
RUN cp build/libs/openCasino_server-*.jar /workspace/app.jar


# ============================================================================
# Stage 2 — runtime: minimal JRE + non-root user, no build tooling
# ============================================================================
FROM ${JRE_IMAGE} AS runtime

ARG APP_USER
ARG APP_UID
ARG APP_GID
ARG APP_HOME

# OCI image labels — picked up by registries, GitLab, and `docker inspect`.
LABEL org.opencontainers.image.title="openCasino_server" \
      org.opencontainers.image.description="Kotlin/Spring WebFlux WebSocket casino server" \
      org.opencontainers.image.source="https://github.com/GoDsqF/openCasino_server" \
      org.opencontainers.image.licenses="See LICENSE"

# Install only what the runtime actually needs:
#   - tini       : PID 1 that reaps zombies and forwards signals (graceful stop)
#   - ca-certificates : refreshed root CAs for outbound TLS (OAuth, etc.)
#   - curl       : used by the HEALTHCHECK; tiny dependency footprint
RUN apt-get update \
 && apt-get install -y --no-install-recommends tini ca-certificates curl \
 && rm -rf /var/lib/apt/lists/*

# Non-root user with explicit UID/GID. Pinning the IDs lets Kubernetes set
# `securityContext.runAsUser: 10001` and `fsGroup: 10001` without surprises,
# and avoids host-mount permission mismatches on Linux runners.
RUN groupadd --system --gid ${APP_GID} ${APP_USER} \
 && useradd  --system --uid ${APP_UID} --gid ${APP_GID} \
             --home-dir ${APP_HOME} --shell /sbin/nologin ${APP_USER} \
 && mkdir -p ${APP_HOME} /certs /config \
 && chown -R ${APP_UID}:${APP_GID} ${APP_HOME} /certs /config

WORKDIR ${APP_HOME}

COPY --from=builder --chown=${APP_UID}:${APP_GID} /workspace/app.jar ./app.jar

# ----------------------------------------------------------------------------
# Runtime defaults. EVERY value below is overridable via `-e` / env / Secret.
# ----------------------------------------------------------------------------
# SERVER_PORT             — HTTP/HTTPS listener port (Spring `server.port`)
# SPRING_PROFILES_ACTIVE  — comma-separated Spring profiles (e.g. "prod,ssl")
# JAVA_OPTS               — extra JVM flags appended to the launch command
# JAVA_TOOL_OPTIONS       — picked up by the JVM automatically; container-aware
#                           memory sizing + better RNG for SSL handshakes
# TZ                      — container time zone; matters for game-loop logs
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE="" \
    JAVA_OPTS="" \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    TZ=UTC

# ----------------------------------------------------------------------------
# Ports
#   8080 — plain HTTP / WebSocket (default Spring port)
#   8443 — HTTPS / WSS when ssl.properties is enabled
# These are documentation only; the orchestrator decides what is published.
# ----------------------------------------------------------------------------
EXPOSE 8080 8443

# ----------------------------------------------------------------------------
# Mount points
#   /certs  — TLS material (cert.pem, privkey.pem, chain.pem). Reference these
#             paths from ssl.properties or via env (see `docker run` examples).
#   /config — optional Spring config drop-in dir; Spring Boot auto-imports
#             `application.properties` placed at /config/application.properties
#             when launched with `--spring.config.additional-location=file:/config/`.
# ----------------------------------------------------------------------------
VOLUME ["/certs", "/config"]

USER ${APP_USER}

# Liveness probe usable by `docker`, Compose, and Swarm. Kubernetes ignores
# this and uses its own livenessProbe / readinessProbe — that's expected.
# We hit the WebSocket upgrade endpoint with a plain GET; Spring returns a
# 4xx/400 quickly, which is enough to prove the JVM is serving requests.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS -o /dev/null -w '%{http_code}' \
        "http://127.0.0.1:${SERVER_PORT}/" | grep -Eq '^[2-4][0-9][0-9]$' || exit 1

# tini -g forwards SIGTERM to the JVM process group, so Spring shuts down
# cleanly when Kubernetes/Compose stops the container.
# `sh -c 'exec java ... "$@"'` lets users append extra Spring args, e.g.
#   docker run ... --spring.config.additional-location=file:/config/
ENTRYPOINT ["/usr/bin/tini", "-g", "--", "sh", "-c", "exec java $JAVA_TOOL_OPTIONS $JAVA_OPTS -jar /opt/app/app.jar \"$@\"", "--"]
CMD []
