import fs from "node:fs";
import path from "node:path";

const [sourcePath, targetPath] = process.argv.slice(2);
if (!sourcePath || !targetPath) throw new Error("source and target paths required");
const spec = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
const routeDir = new URL("../backend/src/main/kotlin/com/companyb/companyapp/api/routes/", import.meta.url);
const dtoDir = new URL("../shared/src/commonMain/kotlin/com/companyb/companyapp/dto/", import.meta.url);
const routeSources = fs.readdirSync(routeDir).filter((name) => name.endsWith("Routes.kt")).map((name) => ({
  name,
  source: fs.readFileSync(new URL(name, routeDir), "utf8"),
}));

function splitTopLevel(value) {
  const result = [];
  let start = 0;
  let depth = 0;
  let quoted = false;
  for (let index = 0; index < value.length; index++) {
    if (value[index] === '"' && value[index - 1] !== "\\") quoted = !quoted;
    if (quoted) continue;
    if ("<([{".includes(value[index])) depth++;
    if (">)]}".includes(value[index])) depth--;
    if (value[index] === "," && depth === 0) {
      result.push(value.slice(start, index));
      start = index + 1;
    }
  }
  result.push(value.slice(start));
  return result;
}

function balancedBlock(source, start) {
  const open = source.indexOf("{", start);
  if (open < 0) return source.slice(start);
  let depth = 0;
  let quoted = false;
  for (let index = open; index < source.length; index++) {
    if (source[index] === '"' && source[index - 1] !== "\\") quoted = !quoted;
    if (quoted) continue;
    if (source[index] === "{") depth++;
    if (source[index] === "}" && --depth === 0) return source.slice(start, index + 1);
  }
  return source.slice(start);
}
function balancedDelimited(source, start, opening, closing) {
  const open = source.indexOf(opening, start);
  if (open < 0) return "";
  let depth = 0;
  let quoted = false;
  for (let index = open; index < source.length; index++) {
    if (source[index] === '"' && source[index - 1] !== "\\") quoted = !quoted;
    if (quoted) continue;
    if (source[index] === opening) depth++;
    if (source[index] === closing && --depth === 0) return source.slice(open, index + 1);
  }
  return source.slice(open);
}

function functionBody(source, handler) {
  const start = source.search(new RegExp(`(?:fun|private fun|internal fun)\\s+(?:[^\\s(]+\\.)?${handler}\\s*\\(`));
  return start < 0 ? "" : balancedBlock(source, start);
}

function handlerSource(source, handler, routeIndex) {
  if (handler) return functionBody(source, handler);
  const lambda = source.indexOf("->", routeIndex);
  const open = lambda < 0 ? -1 : source.lastIndexOf("{", lambda);
  return open < 0 ? "" : balancedBlock(source, open);
}

const dtoModels = {};
for (const file of fs.readdirSync(dtoDir).filter((name) => name.endsWith(".kt"))) {
  const source = fs.readFileSync(new URL(file, dtoDir), "utf8");
  for (const match of source.matchAll(/data class (\w+)\s*\(/g)) {
    const body = balancedDelimited(source, match.index + match[0].length - 1, "(", ")").slice(1, -1);
    const properties = [];
    for (const field of splitTopLevel(body)) {
      const parsed = field.match(/\bval\s+(\w+)\s*:\s*([^=\n]+)/);
      if (parsed) properties.push({ name: parsed[1], type: parsed[2].trim(), defaulted: field.includes("=") });
    }
    dtoModels[match[1]] = properties;
  }
}
const enumValues = {};
for (const file of fs.readdirSync(new URL("../shared/src/commonMain/kotlin/com/companyb/companyapp/domain/", import.meta.url)).filter((name) => name.endsWith(".kt"))) {
  const source = fs.readFileSync(new URL(file, new URL("../shared/src/commonMain/kotlin/com/companyb/companyapp/domain/", import.meta.url)), "utf8");
  for (const match of source.matchAll(/enum class (\w+)[^{]*\{([^}]+)\}/gs)) enumValues[match[1]] = [...match[2].matchAll(/\b[A-Z][A-Z0-9_]*\b/g)].map((item) => item[0]);
}
function scalarSchema(type) {
  const clean = type.replace(/\s/g, "");
  if (clean === "String") return { type: "string" };
  if (clean === "LocalDate") return { type: "string", format: "date" };
  if (["Instant", "OffsetDateTime"].includes(clean)) return { type: "string", format: "date-time" };
  if (clean === "UUID") return { type: "string", format: "uuid" };
  if (["Int", "Short", "Long"].includes(clean)) return { type: "integer" };
  if (["Double", "Float", "BigDecimal"].includes(clean)) return { type: "number" };
  if (clean === "Boolean") return { type: "boolean" };
  if (enumValues[clean]) return { type: "string", enum: enumValues[clean] };
  if (dtoModels[clean]) return { $ref: `#/components/schemas/${clean}` };
  return { type: "object" };
}
function typeSchema(type) {
  const nullable = type.endsWith("?");
  const clean = type.replace(/\?$/, "").replace(/\s/g, "");
  const collection = clean.match(/^(?:List|Set)<(.+)>$/);
  const schema = collection ? { type: "array", items: typeSchema(collection[1]) } : scalarSchema(clean);
  if (!nullable) return schema;
  if (schema.$ref) return { oneOf: [schema, { type: "null" }] };
  return { ...schema, type: Array.isArray(schema.type) ? [...schema.type, "null"] : [schema.type, "null"] };
}
const dtoSchemas = Object.fromEntries(Object.entries(dtoModels).map(([name, properties]) => {
  const required = properties.filter((property) => !property.defaulted && !property.type.endsWith("?")).map((property) => property.name);
  return [name, { type: "object", properties: Object.fromEntries(properties.map((property) => [property.name, typeSchema(property.type)])), ...(required.length ? { required } : {}) }];
}));

function registrations() {
  const result = [];
  for (const { name, source } of routeSources) {
    for (const match of source.matchAll(/(?:config|context)\.routes\.(get|post|patch|delete)\(\s*("(?:[^"\\]|\\.)*"|\$[A-Z0-9_]+)[\s\S]*?(?:::([A-Za-z0-9_]+)|\{\s*context\s*->)/g)) {
      const pathValue = match[2].replace(/\$([A-Z0-9_]+)/g, (_, constant) => source.match(new RegExp(`const val ${constant}\\s*=\\s*"([^"]+)"`))?.[1] || constant.toLowerCase().replace(/_PARAM$/, ""));
      const routePath = pathValue.replace(/"/g, "");
      result.push({ method: match[1], path: routePath, file: name, handler: match[3] || null, source: handlerSource(source, match[3], match.index), fileSource: source });
    }
  }
  return result;
}
const routeRegistrations = registrations();
const byRoute = new Map();
for (const route of routeRegistrations) {
  const key = `${route.method} ${route.path}`;
  if (byRoute.has(key)) throw new Error(`Duplicate production route registration: ${key}`);
  byRoute.set(key, route);
}

const queryOverrides = {
  "/api/allowances": { branchDayId: [true, { type: "string", format: "uuid" }] },
  "/api/branches/{branchId}/daily-summary": { date: [true, { type: "string", format: "date" }] },
  "/api/branches/{branchId}/monthly-summary": { year: [true, { type: "integer" }], month: [true, { type: "integer" }] },
  "/api/branches/{branchId}/export/daily": { date: [true, { type: "string", format: "date" }], format: [true, { type: "string", enum: ["csv", "pdf"] }] },
  "/api/branches/{branchId}/export/range": { from: [true, { type: "string", format: "date" }], to: [true, { type: "string", format: "date" }], format: [true, { type: "string", enum: ["csv", "pdf"] }] },
  "/api/branches/{branchId}/export/monthly": { year: [true, { type: "integer" }], month: [true, { type: "integer" }], format: [true, { type: "string", enum: ["csv", "pdf"] }] },
  "/api/branches/{branchId}/export/all-time": { format: [true, { type: "string", enum: ["csv", "pdf"] }] },
  "/api/branches/export/provincial": { year: [false, { type: "integer" }], month: [false, { type: "integer" }], format: [true, { type: "string", enum: ["csv", "pdf"] }] },
  "/api/branches/export/medical-mission": { year: [false, { type: "integer" }], month: [false, { type: "integer" }], format: [true, { type: "string", enum: ["csv", "pdf"] }] },
  "/api/branches/{branchId}/relief-candidates": { q: [false, { type: "string" }], date: [true, { type: "string", format: "date" }] },
  "/api/audit-log": { tableName: [true, { type: "string" }], recordId: [true, { type: "string", format: "uuid" }] },
  "/api/branches/{branchId}/remittance-sessions": { from: [true, { type: "string", format: "date" }], to: [true, { type: "string", format: "date" }] },
  "/api/branches/{branchId}/remittance-product-sales": { from: [true, { type: "string", format: "date" }], to: [true, { type: "string", format: "date" }] },
  "/api/branches/{branchId}/remittance-days": { from: [true, { type: "string", format: "date" }], to: [true, { type: "string", format: "date" }] },
};
const statusNames = { OK: "200", CREATED: "201", NO_CONTENT: "204", BAD_REQUEST: "400", UNAUTHORIZED: "401", FORBIDDEN: "403", NOT_FOUND: "404", CONFLICT: "409", UNPROCESSABLE_CONTENT: "422", TOO_MANY_REQUESTS: "429", SERVICE_UNAVAILABLE: "503" };

spec.info = { ...spec.info, title: "CompanyApp Backend API", version: "1.0.0" };
spec.components ??= {};
spec.components.securitySchemes ??= {};
spec.components.securitySchemes.BearerAuth = { type: "http", scheme: "bearer", bearerFormat: "JWT" };
spec.components.schemas ??= {};
spec.components.schemas.ErrorResponse ??= { type: "object", required: ["error"], properties: { error: { type: "string" } } };
for (const [name, schema] of Object.entries(dtoSchemas)) spec.components.schemas[name] = schema;

for (const [routePath, methods] of Object.entries(spec.paths ?? {})) {
  for (const [method, operation] of Object.entries(methods)) {
    const registration = byRoute.get(`${method} ${routePath}`);
    if (!registration) throw new Error(`Generated operation is not bound to a route registration: ${method} ${routePath}`);
    operation["x-route-source"] = { file: registration.file, handler: registration.handler || "lambda" };
    operation.responses ??= {};
    const success = Object.keys(operation.responses).find((status) => /^2\d\d$/.test(status));
    if (!success && registration.source.includes("context.json(")) operation.responses["200"] = { description: "OK" };
    const body = registration.source.match(/(?:bodyAsClass|bodyIfPresent)\s*<\s*(\w+)\s*>/);
    if (body && dtoSchemas[body[1]]) operation.requestBody = { required: !registration.source.includes("bodyIfPresent"), content: { "application/json": { schema: { $ref: `#/components/schemas/${body[1]}` } } } };
    if (!body) delete operation.requestBody;
    const responseTypes = [...registration.source.matchAll(/(?:context\.json\([^\n]*?\b(\w+Response)\s*\(|toResponse\(\)\s*:\s*(\w+Response))/g)].flatMap((match) => [match[1], match[2]]).filter((name) => name && dtoSchemas[name]);
    if (registration.source.includes("toResponse()")) {
      const declarations = [...registration.fileSource.matchAll(/toResponse\(\)\s*:\s*(\w+Response)/g)].map((match) => match[1]).filter((name) => dtoSchemas[name]);
      if (declarations.length === 1) responseTypes.push(declarations[0]);
    }
    const responseType = responseTypes[0];
    const successStatus = Object.keys(operation.responses).find((status) => /^2\d\d$/.test(status));
    if (responseType && successStatus) {
      const schema = registration.source.includes(".map") ? { type: "array", items: { $ref: `#/components/schemas/${responseType}` } } : { $ref: `#/components/schemas/${responseType}` };
      operation.responses[successStatus].content = { "application/json": { schema } };
    }
    const explicitStatuses = [...registration.source.matchAll(/HttpStatus\.(\w+)/g)].map((match) => match[1]);
    if (explicitStatuses.length && !explicitStatuses.includes("OK")) delete operation.responses["200"];
    for (const status of explicitStatuses) {
      const code = statusNames[status];
      if (code) operation.responses[code] ??= { description: status.replaceAll("_", " "), ...(code.startsWith("4") ? { content: { "application/json": { schema: { $ref: "#/components/schemas/ErrorResponse" } } } } : {}) };
    }
    if (routePath.startsWith("/api/")) {
      operation.security ??= [{ BearerAuth: [] }];
      for (const code of ["401", "403"]) operation.responses[code] ??= { description: code === "401" ? "Unauthorized" : "Forbidden", content: { "application/json": { schema: { $ref: "#/components/schemas/ErrorResponse" } } } };
    }
    operation.parameters = (operation.parameters || []).filter((parameter) => parameter.in === "path");
    const queries = method === "get" ? { ...(queryOverrides[routePath] || {}) } : {};
    for (const match of registration.source.matchAll(/queryParam\s*\(\s*["']([^"']+)["']/g)) queries[match[1]] ??= [false, { type: "string" }];
    for (const match of registration.source.matchAll(/uuidFromQuery\s*\(\s*["']([^"']+)["']/g)) queries[match[1]] ??= [true, { type: "string", format: "uuid" }];
    for (const [name, [required, schema]] of Object.entries(queries)) operation.parameters.push({ name, in: "query", required, schema });
    for (const [, name] of routePath.matchAll(/\{([^}]+)\}/g)) if (!operation.parameters.some((parameter) => parameter.in === "path" && parameter.name === name)) operation.parameters.push({ name, in: "path", required: true, schema: { type: "string", format: "uuid" } });
  }
}
const operationIds = new Set();
for (const methods of Object.values(spec.paths ?? {})) for (const [method, operation] of Object.entries(methods)) {
  if (!operation.operationId) throw new Error(`Missing operationId for ${method}`);
  if (operationIds.has(operation.operationId)) operation.operationId = `${operation.operationId}_${method}`;
  if (operationIds.has(operation.operationId)) throw new Error(`Duplicate generated operationId: ${operation.operationId}`);
  operationIds.add(operation.operationId);
}
fs.mkdirSync(path.dirname(path.resolve(targetPath)), { recursive: true });
fs.writeFileSync(targetPath, `${JSON.stringify(spec, null, 2)}\n`);
