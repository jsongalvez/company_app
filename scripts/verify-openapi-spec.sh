#!/usr/bin/env bash
set -euo pipefail

spec="backend/build/classes/java/main/openapi-plugin/openapi-default.json"
test -f "$spec"

node - "$spec" <<'NODE'
const fs = require("fs");
const spec = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
if (spec.openapi !== "3.1.0") throw new Error("OpenAPI version is not 3.1.0");
const routeCount = Number(require("child_process").execFileSync("grep", [
  "-rhoE", "config\\.routes\\.(get|post|patch|delete)\\(",
  "backend/src/main/kotlin/com/companyb/companyapp/api/routes",
], { encoding: "utf8" }).trim().split("\n").filter(Boolean).length);
const operations = Object.values(spec.paths || {}).flatMap((path) => Object.values(path));
if (operations.length !== routeCount + 2) {
  throw new Error(`Generated ${operations.length} operations for ${routeCount} production routes plus 2 public routes`);
}
if (operations.some((operation) => !operation.responses || Object.keys(operation.responses).length === 0)) {
  throw new Error("Every generated operation must declare at least one response");
}
if (Object.entries(spec.paths || {}).some(([path, methods]) => path.startsWith("/api/") && Object.values(methods).some((operation) => !operation.security || operation.security.length === 0))) {
  throw new Error("Every protected generated operation must declare bearer security");
}
console.log("OPENAPI_ROUTE_COVERAGE_OK");
NODE

if grep -Fq "JWT_SECRET" "$spec" || grep -Fq "POSTGRES_PASSWORD" "$spec" || grep -Fq "TEST_PASSWORD" "$spec"; then
  echo "OpenAPI secret scan failed" >&2
  exit 1
fi
echo "OPENAPI_SECRET_SCAN_OK"
