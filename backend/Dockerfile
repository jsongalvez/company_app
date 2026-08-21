# Multi-stage: build with the Gradle wrapper, run on a plain JRE.
# Deployed via Coolify (build pack: Dockerfile).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew gradlew
COPY gradle gradle
COPY gradle.properties gradle.properties
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY shared shared
COPY backend backend
COPY composeApp composeApp
RUN chmod +x gradlew && ./gradlew :backend:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/backend/build/install/backend/ /app/
# dotenv-kotlin fails on a missing .env file; the real values come from the
# container environment (Coolify env vars take precedence).
RUN touch .env
ENTRYPOINT ["bin/backend"]
