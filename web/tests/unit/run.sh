#!/usr/bin/env bash
# Pure-logic tests for web/src/lib: bundle each *.test.ts with rolldown, run with node:test. Run from web/.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
for f in tests/unit/*.test.ts; do
  npx rolldown "$f" --file "$out/$(basename "${f%.ts}").mjs" --format esm --platform node >/dev/null
done
node --test "$out"/*.mjs
