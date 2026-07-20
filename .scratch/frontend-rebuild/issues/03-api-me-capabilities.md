# 03 — GET /api/me + /api/me/capabilities

**What to build:** Two new backend endpoints so the frontend can display the current user's identity and gate UI elements by capabilities.

- `GET /api/me` returns `{ id, username, status, createdAt }`. No capability gate — the caller is always themselves.
- `GET /api/me/capabilities` returns `[{ capabilityCode, contextType, contextId, sourceType }]` from the `active_user_capabilities` view. No capability gate.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] `MeResponse` DTO in shared module with `id`, `username`, `status`, `createdAt`
- [ ] `UserCapabilityResponse` DTO in shared module with `capabilityCode`, `contextType`, `contextId`, `sourceType`
- [ ] `MeService.getMe(userId)` queries `app_user` by id
- [ ] `MeService.getCapabilities(userId)` queries `active_user_capabilities` view by user_id
- [ ] `MeRoutes` registered in `Main.kt` under `/api/*` (authenticated)
- [ ] `GET /api/me` returns 200 with user data, 401 without JWT
- [ ] `GET /api/me/capabilities` returns 200 with capability list (empty array if ONBOARDING or user with no caps), 401 without JWT
- [ ] Integration tests: `MeServicePostgresTest.kt` covering success, no capabilities, unauthenticated, inactive user exclusion
- [ ] `./gradlew :backend:detekt :backend:ktlintCheck :backend:test` passes
