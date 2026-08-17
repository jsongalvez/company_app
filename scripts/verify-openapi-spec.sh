#!/usr/bin/env bash
set -euo pipefail

spec="backend/build/tmp/kapt3/classes/main/openapi-plugin/openapi-default.json"
test -f "$spec"

node - "$spec" <<'NODE'
const fs = require("fs");
const spec = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
if (spec.openapi !== "3.1.0") throw new Error("OpenAPI version is not 3.1.0");

const routeDir = "backend/src/main/kotlin/com/companyb/companyapp/api/routes";
const constants = {};
const routes = [];
for (const file of fs.readdirSync(routeDir).filter((name) => name.endsWith(".kt"))) {
  const source = fs.readFileSync(`${routeDir}/${file}`, "utf8");
  for (const match of source.matchAll(/const val ([A-Z0-9_]+)\s*=\s*"([^"]+)"/g)) constants[match[1]] = match[2];
  for (const match of source.matchAll(/routes\.(get|post|patch|delete)\s*\(\s*"([^"]+)"/g)) {
    const path = match[2].replace(/\{\$([A-Z0-9_]+)\}/g, (_, name) => `{${constants[name] || name.toLowerCase().replace(/_PARAM$/, "")}}`);
    routes.push(`${match[1]} ${path}`);
  }
}
const generated = Object.entries(spec.paths || {}).flatMap(([path, methods]) =>
  Object.keys(methods).map((method) => `${method} ${path}`));
const expected = [...new Set(routes)].sort();
const actual = [...new Set(generated)].sort();
if (JSON.stringify(actual) !== JSON.stringify(expected)) {
  throw new Error(`Generated route set differs. Missing: ${expected.filter((route) => !actual.includes(route))}; unexpected: ${actual.filter((route) => !expected.includes(route))}`);
}
const operations = Object.values(spec.paths || {}).flatMap((path) => Object.values(path));
if (operations.some((operation) => !operation.responses || Object.keys(operation.responses).length === 0)) {
  throw new Error("Every generated operation must declare at least one response");
}
if (Object.entries(spec.paths || {}).some(([path, methods]) => path.startsWith("/api/") && Object.values(methods).some((operation) => !operation.security || operation.security.length === 0))) {
  throw new Error("Every protected generated operation must declare bearer security");
}
if (!spec.info?.title || !spec.info?.version) throw new Error("OpenAPI info metadata is empty");
if (!spec.components?.securitySchemes?.BearerAuth) throw new Error("BearerAuth security scheme is missing");
for (const [path, methods] of Object.entries(spec.paths || {})) {
  for (const [method, operation] of Object.entries(methods)) {
    for (const name of path.matchAll(/\{([^}]+)\}/g)) {
      if (!(operation.parameters || []).some((parameter) => parameter.in === "path" && parameter.name === name[1] && parameter.required)) {
        throw new Error(`${method.toUpperCase()} ${path} is missing required path parameter ${name[1]}`);
      }
    }
  }
}
console.log("OPENAPI_ROUTE_COVERAGE_OK");
NODE

if grep -Fq "JWT_SECRET" "$spec" || grep -Fq "POSTGRES_PASSWORD" "$spec" || grep -Fq "TEST_PASSWORD" "$spec"; then
  echo "OpenAPI secret scan failed" >&2
  exit 1
fi
echo "OPENAPI_SECRET_SCAN_OK"
