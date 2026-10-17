# CompanyApp

Internal operations app for a multi-branch physical therapy practice: sessions,
clients, stock, expenses and pay, remittance, relief duty. See
[`CONTEXT.md`](CONTEXT.md) for the shared vocabulary.

- `backend/` — Kotlin + Javalin API, PostgreSQL via Flyway. Also serves the web
  app when `WEB_DIR` is set.
- `shared/` — Kotlin contracts (DTOs, enums, rules) used by the backend.
- `web/` — the web app: Svelte 5 + Vite, typed against the backend's OpenAPI spec.
- `detekt-rules/` — custom code checks.

## Local development

Needs JDK 21 (`mise install` picks it up from `.mise.toml`), Node 24+, and
PostgreSQL 18 (`docker compose -f docker/docker-compose.yml up -d`, or any local
server).

```bash
cp .env.example .env              # fill in DB, JWT and dev login values
./gradlew :backend:run            # dev server: migrates, seeds dev logins, serves the API
cd web && npm install && npm run dev   # http://localhost:5173, proxies the API to APP_PORT
```

Production-style: `cd web && npm run build`, set `WEB_DIR=web/dist`, and the
backend serves the app and the API from one port.

After changing an API endpoint, regenerate the web app's types:

```bash
./gradlew :backend:exportOpenApiSpec && cd web && npm run gen:api
```

## Checks

```bash
./gradlew ktlintCheck detekt :backend:test :shared:jvmTest
cd web && npm run check && npm run lint
```

## Deploy

Coolify builds `backend/Dockerfile` from the repo root. The image contains the
backend and the built web app; set the backend env vars from `.env.example` in
Coolify.
