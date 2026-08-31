#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"
spec="${1:-$repo_root/backend/build/tmp/kapt3/classes/main/openapi-plugin/openapi-default.json}"
test -f "$spec"
freshness_inputs=(
  "$repo_root/backend/src/main/kotlin/com/companyb/companyapp/api/routes"
  "$repo_root/backend/src/main/kotlin/com/companyb/companyapp/service"
)
mapping_dir="$repo_root/backend/src/main/kotlin/com/companyb/companyapp/api/mapping"
if [ -d "$mapping_dir" ]; then
  freshness_inputs+=("$mapping_dir")
fi
freshness_inputs+=(
  "$repo_root/shared/src/commonMain/kotlin/com/companyb/companyapp/dto"
  "$repo_root/shared/src/commonMain/kotlin/com/companyb/companyapp/domain"
  "$repo_root/scripts/normalize-openapi-spec.mjs"
  "$repo_root/scripts/openapi-source-parser.mjs"
  "$repo_root/scripts/openapi-route-contract.json"
  "$repo_root/scripts/verify-openapi-spec.sh"
)
if [ "${OPENAPI_VERIFY_SKIP_FRESHNESS:-0}" != "1" ] && find "${freshness_inputs[@]}" \
     -type f -newer "$spec" -print -quit | grep -q .; then
  echo "Generated OpenAPI artifact is older than source inputs" >&2
  exit 1
fi
normalized_spec=$(mktemp)
trap 'rm -f "$normalized_spec"' EXIT
node "$repo_root/scripts/normalize-openapi-spec.mjs" "$spec" "$normalized_spec"
spec="$normalized_spec"

OPENAPI_ROUTE_DIR="$repo_root/backend/src/main/kotlin/com/companyb/companyapp/api/routes" node --input-type=module - "$spec" <<'NODE'
import fs from "node:fs";
import crypto from "node:crypto";
import { balancedDelimited, sourceAnnotations as parseSourceAnnotations, withoutComments } from "./scripts/openapi-source-parser.mjs";
const spec = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
if (spec.openapi !== "3.1.0") throw new Error("OpenAPI version is not 3.1.0");

const routeDir = process.env.OPENAPI_ROUTE_DIR;
if (!routeDir) throw new Error("OPENAPI_ROUTE_DIR is required");
const constants = {};
const sharedRoutes = fs.readFileSync(`${process.cwd()}/shared/src/commonMain/kotlin/com/companyb/companyapp/api/ApiRoutes.kt`, "utf8");
for (const match of sharedRoutes.matchAll(/const val (\w+)\s*=\s*"([^"]*)"/g)) constants[match[1]] = match[2];
function resolveApiRoute(value) {
  let resolved = value;
  for (let pass = 0; pass < 10; pass++) {
    const next = resolved.replace(/\$([A-Z][A-Z0-9_]*)/g, (_, name) => constants[name] || `$${name}`);
    if (next === resolved) return next;
    resolved = next;
  }
  return resolved;
}
const routes = [];
const annotated = new Map();
const annotationSources = new Map();
function sourceAnnotations(source, file) {
  return parseSourceAnnotations(source, file, (value) => resolveApiRoute(constants[value] || value)).map((annotation) => ({
    ...annotation,
    annotation: annotation.source,
  }));
}
function openApiParams(annotation) {
  const entries = [];
  let cursor = 0;
  while (true) {
    const start = annotation.indexOf('OpenApiParam', cursor);
    if (start < 0) return entries;
    const open = annotation.indexOf('(', start);
    if (open < 0) throw new Error('OpenApiParam has no constructor');
    const value = balancedDelimited(annotation, open, '(', ')');
    entries.push(value.slice(1, -1));
    cursor = open + value.length;
  }
}
for (const file of fs.readdirSync(routeDir).filter((name) => name.endsWith(".kt"))) {
  const source = fs.readFileSync(`${routeDir}/${file}`, "utf8");
  const scanSource = withoutComments(source);
  for (const match of scanSource.matchAll(/const val ([A-Z0-9_]+)\s*=\s*"([^"]+)"/g)) constants[match[1]] = match[2];
  for (const match of sourceAnnotations(source, file)) {
    const annotation = match.annotation;
    const pathParamBlocks = [...annotation.matchAll(/pathParams\s*=\s*\[[\s\S]*?\]/g)].map((params) => params[0]);
    const pathParams = pathParamBlocks.flatMap((params) => [...params.matchAll(/name\s*=\s*"([^"]+)"/g)].map((param) => param[1]));
    if (pathParams.length > 0 && (!/type\s*=\s*UUID::class/.test(annotation) || !/required\s*=\s*true/.test(annotation))) throw new Error(`Source OpenAPI path parameter type/required metadata invalid: ${file} ${match.path}`);
    // Scan only the pathParams arrays: the same annotation can declare queryParams
    // whose entries legitimately omit type=UUID/required=true (#367).
    const pathParamEntries = pathParamBlocks.flatMap((block) => openApiParams(block));
    if (pathParamEntries.some((param) => !/name\s*=\s*"[^"]+"/.test(param) || !/type\s*=\s*UUID::class/.test(param) || !/required\s*=\s*true/.test(param))) throw new Error(`Source OpenAPI path parameter entry invalid: ${file} ${match.path}`);
    const sourceParams = [...match.path.matchAll(/\{([^}]+)\}/g)].map((param) => param[1]);
    if (JSON.stringify(pathParams.sort()) !== JSON.stringify(sourceParams.sort())) throw new Error(`Source OpenAPI path params differ: ${file} ${match.path}`);
    for (const method of match.methods) {
      const key = `${method} ${match.path}`;
      if (annotated.has(key)) throw new Error(`Duplicate source OpenAPI annotation: ${key}`);
      annotated.set(key, file);
      annotationSources.set(key, {
        file,
        owner: match.owner,
        operationId: match.operationId,
        source: source.slice(match.start, match.end),
      });
    }
  }
  for (const match of scanSource.matchAll(/routes\.(get|post|patch|delete|put)\s*\(\s*(?:"([^"]+)"|ApiRoutes\.(\w+))/g)) {
    const path = resolveApiRoute(match[2] || constants[match[3]] || "").replace(/\{\$([A-Z0-9_]+)\}/g, (_, name) => `{${constants[name] || name.toLowerCase().replace(/_PARAM$/, "")}}`);
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
if (operations.some((operation) => !operation["x-route-source"] || typeof operation["x-route-source"].file !== "string" || typeof operation["x-route-source"].registration !== "string" || operation["x-route-source"].registration.length === 0 || typeof operation["x-route-source"].selectedHandlerSource !== "string" || operation["x-route-source"].selectedHandlerSource.trim().length === 0 || !Number.isInteger(operation["x-route-source"].selectedHandlerStart) || !Number.isInteger(operation["x-route-source"].selectedHandlerEnd))) {
  throw new Error("Every operation must retain its exact route registration source binding");
}
for (const [path, methods] of Object.entries(spec.paths || {})) for (const [method, operation] of Object.entries(methods)) {
  const routeBinding = operation["x-route-source"];
  const annotationBinding = operation["x-openapi-source"];
  const expectedAnnotation = annotationSources.get(`${method} ${path}`);
  if (!annotationBinding || annotationBinding.key !== `${method} ${path}` || annotationBinding.file !== expectedAnnotation?.file || annotationBinding.owner !== expectedAnnotation?.owner || annotationBinding.operationId !== expectedAnnotation?.operationId || annotationBinding.annotation !== expectedAnnotation?.source) {
    throw new Error(`${method.toUpperCase()} ${path} does not retain exact source OpenApi annotation binding`);
  }
  if (annotationBinding.operationId !== operation.operationId) {
    throw new Error(`${method.toUpperCase()} ${path} generated operationId differs from source operationId`);
  }
  const routeSource = fs.readFileSync(`${routeDir}/${routeBinding.file}`, "utf8");
   if (routeSource.split(routeBinding.registration).length - 1 !== 1) throw new Error(`${method.toUpperCase()} ${path} route registration binding is not unique or stale`);
  if (!routeBinding.key || routeBinding.key !== `${method} ${path}`) throw new Error(`${method.toUpperCase()} ${path} route registration key is not exact`);
  if (!routeBinding.owner || routeBinding.owner !== annotationBinding.owner) throw new Error(`${method.toUpperCase()} ${path} annotation and registration have different owners`);
    const expectedHash = crypto.createHash("sha256").update(routeBinding.selectedHandlerSource).digest("hex");
   if (routeBinding.selectedHandlerHash !== expectedHash) throw new Error(`${method.toUpperCase()} ${path} selected handler source hash is missing or invalid`);
   if (routeBinding.selectedHandlerStart < 0 || routeBinding.selectedHandlerEnd !== routeBinding.selectedHandlerStart + routeBinding.selectedHandlerSource.length || routeSource.slice(routeBinding.selectedHandlerStart, routeBinding.selectedHandlerEnd) !== routeBinding.selectedHandlerSource) throw new Error(`${method.toUpperCase()} ${path} selected handler source range is stale`);
   if (routeSource.split(routeBinding.selectedHandlerSource).length - 1 !== 1) throw new Error(`${method.toUpperCase()} ${path} selected handler source binding is not unique or stale`);
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
