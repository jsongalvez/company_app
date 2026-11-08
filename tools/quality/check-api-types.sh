#!/usr/bin/env bash
# Fails when web/src/api/schema.d.ts is not what the server's OpenAPI spec generates, so the web
# never type-checks against a contract the server no longer serves.
# Fix: ./gradlew :backend:exportOpenApiSpec && (cd web && npm run gen:api)
#
# Usage: tools/quality/check-api-types.sh [TYPES_FILE]   (default web/src/api/schema.d.ts)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TYPES="$(realpath "${1:-$ROOT/web/src/api/schema.d.ts}")"
cd "$ROOT"
./gradlew :backend:exportOpenApiSpec -q >/dev/null
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
(cd web && npx openapi-typescript ../backend/build/openapi/openapi-canonical.json -o "$out/schema.d.ts" >/dev/null 2>&1)
if cmp -s "$out/schema.d.ts" "$TYPES"; then
  echo "check-api-types: OK"
else
  diff "$TYPES" "$out/schema.d.ts" | head -20 >&2 || true
  echo "check-api-types: $TYPES is stale; run ./gradlew :backend:exportOpenApiSpec && (cd web && npm run gen:api)" >&2
  exit 1
fi
