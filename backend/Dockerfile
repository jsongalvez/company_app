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

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/backend/build/install/backend/ /app/
COPY --from=web /web/dist/ /app/web/
ENV WEB_DIR=/app/web
# dotenv-kotlin fails on a missing .env file; the real values come from the
# container environment (Coolify env vars take precedence).
RUN touch .env
ENTRYPOINT ["bin/backend"]
