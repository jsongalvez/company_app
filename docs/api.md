# CompanyApp API — export and authenticated import

The canonical OpenAPI document is the single source for inspecting and importing
the API (#495 contract; the old Node source parser and the hand-maintained
Postman endpoint catalog were deleted in #496). Interactive docs are served by
the backend itself at `/swagger` (JSON at `/openapi.json`).

## Export

```bash
./gradlew :backend:exportOpenApiSpec
```

Writes `backend/build/openapi/openapi-canonical.json`. This is a local build
artifact — never commit it. It is the same document production serves (same
kapt resource through the same `OpenApiCanonical` transform).

## Import (Postman / Insomnia / any OpenAPI client)

1. Import the exported `openapi-canonical.json` file.
2. Set a `base_url` variable to the server (default `http://localhost:8080`).
3. Set a `token` variable (initially empty). Protected endpoints send
   `Authorization: Bearer {{token}}` automatically.
4. Log in once: `POST {{base_url}}/auth/login` with
   `{"username": "<user>", "password": "<password>"}`. Capture the response's
   `token` field into the `token` variable (in Postman, a login post-response
   script like `pm.collectionVariables.set('token', pm.response.json().token)`).
   Never log or print the token value.

## Wire conventions

- Most entity IDs are client-generated UUIDs — the caller provides them.
- Dates use ISO-8601 format (e.g. `2026-07-14`).
- Monetary amounts are strings (e.g. `"1500.00"`).
