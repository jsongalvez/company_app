# Multi-stage: build the web app and the backend, run on a plain JRE.
# Deployed via Coolify (build pack: Dockerfile, build context: repo root).
FROM node:24-slim AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npx vite build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew gradlew
COPY gradle gradle
COPY gradle.properties gradle.properties
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY shared shared
COPY backend backend
COPY detekt-rules detekt-rules
RUN chmod +x gradlew && ./gradlew :backend:installDist --no-daemon

# Temurin 21.0.13 fixes CVE-2026-91403, but the eclipse-temurin:21-jre image
# still ships 21.0.12.1. Swap in the 21.0.13 JRE from Adoptium, checksum-pinned.
# Drop this stage once `eclipse-temurin:21-jre` gives 21.0.13 or later.
FROM eclipse-temurin:21-jre AS jre
ARG TARGETARCH
RUN set -eu; \
    case "${TARGETARCH:-arm64}" in \
      arm64) a=aarch64; sum=b1734db7ade49c3f6acc6ae06233b8c2dd90e3e2f00f67000ae1c972aaa64d8c ;; \
      amd64) a=x64; sum=a535a1df9c6284d9618872984e026ae0352d1f7e9e0605425c0e6114f47351dd ;; \
      *) echo "no JRE for $TARGETARCH" >&2; exit 1 ;; \
    esac; \
    curl -fsSL -o /tmp/jre.tgz "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jre_${a}_linux_hotspot_21.0.13_9.tar.gz"; \
    echo "$sum  /tmp/jre.tgz" | sha256sum -c -; \
    rm -rf "$JAVA_HOME"; mkdir -p "$JAVA_HOME"; \
    tar -xzf /tmp/jre.tgz -C "$JAVA_HOME" --strip-components=1; \
    rm /tmp/jre.tgz; \
    java -version 2>&1 | grep -q '"21.0.13'

FROM jre
WORKDIR /app
COPY --from=build /src/backend/build/install/backend/ /app/
COPY --from=web /web/dist/ /app/web/
ENV WEB_DIR=/app/web
# dotenv-kotlin fails on a missing .env file; the real values come from the
# container environment (Coolify env vars take precedence).
RUN touch .env
ENTRYPOINT ["bin/backend"]
