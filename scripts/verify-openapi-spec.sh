#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
spec="$repo_root/backend/build/tmp/kapt3/classes/main/openapi-plugin/openapi-default.json"
test -f "$spec"
node "$repo_root/scripts/normalize-openapi-spec.mjs" "$spec" "$spec"

OPENAPI_ROUTE_DIR="$repo_root/backend/src/main/kotlin/com/companyb/companyapp/api/routes" node - "$spec" <<'NODE'
const fs = require("fs");
const spec = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
if (spec.openapi !== "3.1.0") throw new Error("OpenAPI version is not 3.1.0");

const routeDir = process.env.OPENAPI_ROUTE_DIR;
if (!routeDir) throw new Error("OPENAPI_ROUTE_DIR is required");
const constants = {};
const routes = [];
const annotated = new Map();
for (const file of fs.readdirSync(routeDir).filter((name) => name.endsWith(".kt"))) {
  const source = fs.readFileSync(`${routeDir}/${file}`, "utf8");
  for (const match of source.matchAll(/const val ([A-Z0-9_]+)\s*=\s*"([^"]+)"/g)) constants[match[1]] = match[2];
  for (const match of source.matchAll(/@OpenApi\([\s\S]*?path\s*=\s*"([^"]+)"[\s\S]*?methods\s*=\s*\[([^\]]+)\][\s\S]*?\)/g)) {
    const annotation = source.slice(match.index, source.indexOf("operationId", match.index));
    const pathParams = [...annotation.matchAll(/pathParams\s*=\s*\[[\s\S]*?\]/g)].flatMap((params) => [...params[0].matchAll(/name\s*=\s*"([^"]+)"/g)].map((param) => param[1]));
    if (pathParams.length > 0 && (!/type\s*=\s*UUID::class/.test(annotation) || !/required\s*=\s*true/.test(annotation))) throw new Error(`Source OpenAPI path parameter type/required metadata invalid: ${file} ${match[1]}`);
    const pathParamEntries = [...annotation.matchAll(/OpenApiParam\(([\s\S]*?)\)/g)].map((param) => param[1]);
    if (pathParamEntries.some((param) => !/name\s*=\s*"[^"]+"/.test(param) || !/type\s*=\s*UUID::class/.test(param) || !/required\s*=\s*true/.test(param))) throw new Error(`Source OpenAPI path parameter entry invalid: ${file} ${match[1]}`);
    const sourceParams = [...match[1].matchAll(/\{([^}]+)\}/g)].map((param) => param[1]);
    if (JSON.stringify(pathParams.sort()) !== JSON.stringify(sourceParams.sort())) throw new Error(`Source OpenAPI path params differ: ${file} ${match[1]}`);
    for (const method of match[2].matchAll(/HttpMethod\.(GET|POST|PATCH|DELETE)/g)) {
      const key = `${method[1].toLowerCase()} ${match[1]}`;
      if (annotated.has(key)) throw new Error(`Duplicate source OpenAPI annotation: ${key}`);
      annotated.set(key, file);
    }
  }
  for (const match of source.matchAll(/routes\.(get|post|patch|delete)\s*\(\s*"([^"]+)"/g)) {
    const path = match[2].replace(/\{\$([A-Z0-9_]+)\}/g, (_, name) => `{${constants[name] || name.toLowerCase().replace(/_PARAM$/, "")}}`);
    routes.push(`${match[1]} ${path}`);
  }
}
const generated = Object.entries(spec.paths || {}).flatMap(([path, methods]) =>
  Object.keys(methods).map((method) => `${method} ${path}`));
const expected = [...new Set(routes)].sort();
const actual = [...new Set(generated)].sort();
if (routes.length !== new Set(routes).size) throw new Error("Duplicate production route registration");
if (JSON.stringify(actual) !== JSON.stringify(expected)) {
  throw new Error(`Generated route set differs. Missing: ${expected.filter((route) => !actual.includes(route))}; unexpected: ${actual.filter((route) => !expected.includes(route))}`);
}
for (const route of expected) if (!annotated.has(route)) throw new Error(`Route has no source OpenAPI annotation: ${route}`);
for (const route of annotated.keys()) if (!expected.includes(route)) throw new Error(`OpenAPI annotation has no route registration: ${route}`);
const operations = Object.values(spec.paths || {}).flatMap((path) => Object.values(path));
if (operations.some((operation) => !operation.responses || Object.keys(operation.responses).length === 0)) {
  throw new Error("Every generated operation must declare at least one response");
}
if (Object.entries(spec.paths || {}).some(([path, methods]) => path.startsWith("/api/") && Object.values(methods).some((operation) => !operation.security || operation.security.length === 0))) {
  throw new Error("Every protected generated operation must declare bearer security");
}
if (operations.some((operation) => operation.operationId == null || operation.operationId === "")) throw new Error("Every operation must have an operationId");
const operationIds = operations.map((operation) => operation.operationId);
if (new Set(operationIds).size !== operationIds.length) throw new Error("Operation IDs must be unique");
if (operations.some((operation) => operation.requestBody?.content?.["application/json"]?.schema?.type === "object")) {
  throw new Error("Request bodies must reference DTO schemas, not generic objects");
}
for (const [path, methods] of Object.entries(spec.paths || {})) for (const [method, operation] of Object.entries(methods)) {
  for (const [status, response] of Object.entries(operation.responses || {})) {
    if (!(path === "/health" && status === "503") && /^4\d\d$|^5\d\d$/.test(status) && response.content?.["application/json"]?.schema?.$ref !== "#/components/schemas/ErrorResponse") {
      throw new Error(`${method.toUpperCase()} ${path} error ${status} must use ErrorResponse`);
    }
    const explicitlyBodyless =
      status === "204" ||
      (method === "post" && (path === "/api/auth/logout" || path === "/auth/register")) ||
      (method === "post" && path === "/api/branches/{branchId}/inventory" && status === "201");
    if (/^2\d\d$/.test(status) && !explicitlyBodyless && !response.content) {
      throw new Error(`${method.toUpperCase()} ${path} success ${status} must declare response content or be explicitly bodyless`);
    }
  }
  if (!Object.keys(operation.responses || {}).some((status) => /^2\d\d$/.test(status))) {
    throw new Error(`${method.toUpperCase()} ${path} must declare a success response`);
  }
}
if (operations.some((operation) => !operation["x-route-source"] || typeof operation["x-route-source"].file !== "string" || typeof operation["x-route-source"].registration !== "string" || operation["x-route-source"].registration.length === 0)) {
  throw new Error("Every operation must retain its exact route registration source binding");
}
function assertRefs(value) {
  if (!value || typeof value !== "object") return;
  if (typeof value.$ref === "string" && value.$ref.startsWith("#/components/schemas/")) {
    const name = value.$ref.slice("#/components/schemas/".length);
    if (!spec.components?.schemas?.[name]) throw new Error(`Unresolved schema reference: ${value.$ref}`);
  }
  for (const child of Object.values(value)) assertRefs(child);
}
assertRefs(spec.paths);
assertRefs(spec.components);
function assertSchemas(value, location = "schema") {
  if (!value || typeof value !== "object") return;
  if (value.type === "object" && !value.$ref && !value.properties && location !== "schema.ErrorResponse") {
    throw new Error(`Generic object schema is not allowed at ${location}`);
  }
  for (const [key, child] of Object.entries(value)) assertSchemas(child, `${location}.${key}`);
}
assertSchemas(spec.components);
for (const operation of operations) {
  for (const parameter of operation.parameters || []) {
    if (parameter.in === "query" && !parameter.schema) throw new Error(`Query parameter ${parameter.name} has no schema`);
  }
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
NODE

if grep -Fq "JWT_SECRET" "$spec" || grep -Fq "POSTGRES_PASSWORD" "$spec" || grep -Fq "TEST_PASSWORD" "$spec"; then
  echo "OpenAPI secret scan failed" >&2
  exit 1
fi
echo "OPENAPI_ROUTE_COVERAGE_OK"
echo "OPENAPI_SECRET_SCAN_OK"
