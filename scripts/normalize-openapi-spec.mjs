import fs from "node:fs";
import path from "node:path";

const [sourcePath, targetPath] = process.argv.slice(2);
if (!sourcePath || !targetPath) throw new Error("source and target paths required");

const spec = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
const routeDir = new URL("../backend/src/main/kotlin/com/companyb/companyapp/api/routes/", import.meta.url);
const routeSources = fs.readdirSync(routeDir).filter((name) => name.endsWith("Routes.kt")).map((name) => ({
  name,
  source: fs.readFileSync(new URL(name, routeDir), "utf8"),
}));

const dtoSchemas = {};
for (const file of fs.readdirSync(new URL("../shared/src/commonMain/kotlin/com/companyb/companyapp/dto/", import.meta.url))) {
  if (!file.endsWith(".kt")) continue;
  const source = fs.readFileSync(new URL(file, new URL("../shared/src/commonMain/kotlin/com/companyb/companyapp/dto/", import.meta.url)), "utf8");
  for (const match of source.matchAll(/data class (\w+)\s*\(([^)]*)\)/gs)) {
    const properties = {};
    dtoSchemas[match[1]] = { type: "object", properties };
    for (const property of match[2].split(",")) {
      const field = property.match(/\bval\s+(\w+)\s*:\s*([\w?.<>]+)/);
      if (!field) continue;
      const [, name, type] = field;
      const primitive = type.replace("?", "");
      const nullable = type.endsWith("?");
      const baseType = primitive.replace("?", "");
      const list = baseType.match(/^(?:List|Set)<(.+)>$/);
      const scalar = (value) => value === "String" || value === "UUID" ? { type: "string" } :
        ["Int", "Short", "Long"].includes(value) ? { type: "integer" } :
        value === "Boolean" ? { type: "boolean" } :
        ["Double", "Float", "BigDecimal"].includes(value) ? { type: "number" } :
        value.endsWith("Type") || value.endsWith("Status") || value === "Gender" ? { type: "string" } :
        dtoSchemas[value] ? { $ref: `#/components/schemas/${value}` } : { type: "object" };
      const schema = list ? { type: "array", items: scalar(list[1]) } : scalar(baseType);
      if (nullable) schema.nullable = true;
      properties[name] = schema;
      if (!nullable && !property.includes("=")) (dtoSchemas[match[1]].required ??= []).push(name);
    }
  }
}

function balancedBlock(source, start) {
  const open = source.indexOf("{", start);
  if (open < 0) return source.slice(start);
  let depth = 0;
  let quoted = false;
  for (let index = open; index < source.length; index++) {
    const character = source[index];
    if (character === '"' && source[index - 1] !== "\\") quoted = !quoted;
    if (quoted) continue;
    if (character === "{") depth++;
    if (character === "}" && --depth === 0) return source.slice(start, index + 1);
  }
  return source.slice(start);
}

function functionBody(source, handler) {
  const start = source.search(new RegExp(`(?:fun|private fun|internal fun)\\s+(?:[^\\s(]+\\.)?${handler}\\s*\\(`));
  return start < 0 ? "" : balancedBlock(source, start);
}

function handlerSource(source, handler, routeIndex) {
  if (handler) return functionBody(source, handler);
  const lambda = source.indexOf("->", routeIndex);
  return lambda < 0 ? "" : balancedBlock(source, lambda);
}

function registrations() {
  const result = [];
  for (const { name, source } of routeSources) {
    for (const match of source.matchAll(/(?:config|context)\.routes\.(get|post|patch|delete)\(\s*("(?:[^"\\]|\\.)*"|\$[A-Z0-9_]+)[\s\S]*?(?:::([A-Za-z0-9_]+)|\{\s*context\s*->)/g)) {
      const path = match[2].replace(/\$([A-Z0-9_]+)/g, (_, constant) => {
        const value = source.match(new RegExp(`const val ${constant}\\s*=\\s*"([^"]+)"`));
        return value ? value[1] : constant.toLowerCase().replace(/_PARAM$/, "");
      }).replace(/"/g, "");
      const handler = match[3] || null;
      result.push({ method: match[1], path, file: name, handler, source: handlerSource(source, handler, match.index), fileSource: source });
    }
  }
  return result;
}

const routeRegistrations = registrations();
const byRoute = new Map(routeRegistrations.map((route) => [`${route.method} ${route.path}`, route]));
spec.info = { ...spec.info, title: "CompanyApp Backend API", version: "1.0.0" };
spec.components ??= {};
spec.components.securitySchemes ??= {};
spec.components.securitySchemes.BearerAuth = { type: "http", scheme: "bearer", bearerFormat: "JWT" };
spec.components.schemas ??= {};
spec.components.schemas.ErrorResponse ??= {
  type: "object",
  properties: { error: { type: "string" } },
};

for (const [path, methods] of Object.entries(spec.paths ?? {})) {
  for (const [method, operation] of Object.entries(methods)) {
    const registration = byRoute.get(`${method} ${path}`);
    if (!registration) throw new Error(`Generated operation is not bound to a route registration: ${method} ${path}`);
    operation["x-route-source"] = `${registration.file}:${registration.handler || "lambda"}`;
    operation.responses ??= {};
    operation.responses["200"] ??= { description: "OK" };
    const body = registration.source.match(/(?:bodyAsClass|bodyIfPresent)\s*<\s*(\w+)\s*>/);
    if (body && !operation.requestBody) {
      const schema = dtoSchemas[body[1]];
      if (!schema) throw new Error(`No DTO schema found for ${body[1]} (${method} ${path})`);
      operation.requestBody = {
        required: true,
        content: { "application/json": { schema: { $ref: `#/components/schemas/${body[1]}` } } },
      };
    }
    if (!body) delete operation.requestBody;
    const responseType =
      registration.source.match(/context\.json\([^\n)]*\b(\w+Response)\s*\(/)?.[1] ||
      null;
    if (responseType && dtoSchemas[responseType]) {
      const successStatus = operation.responses["201"] ? "201" : operation.responses["200"] ? "200" : null;
      if (successStatus) {
        operation.responses[successStatus].content = {
          "application/json": { schema: { $ref: `#/components/schemas/${responseType}` } },
        };
      }
    }
    operation.parameters = (operation.parameters || []).filter((parameter) => parameter.in === "path");
    const knownQueries = {
      "/api/clients": ["q"],
      "/api/branches/{branchId}/daily-summary": ["date"],
      "/api/branches/{branchId}/daily-summaries": ["cursor", "limit", "from", "to"],
      "/api/branches/{branchId}/relief-candidates": ["q", "date"],
      "/api/audit-log": ["tableName", "recordId"],
      "/api/audit-log/entries": ["tableName", "action", "callerName", "dateFrom", "dateTo", "cursor", "limit"],
      "/api/remittances": ["status"],
      "/api/branches/{branchId}/remittance-sessions": ["from", "to"],
      "/api/branches/{branchId}/remittance-product-sales": ["from", "to"],
      "/api/branches/{branchId}/remittance-days": ["from", "to"],
      "/api/branches/{branchId}/monthly-summary": ["year", "month"],
      "/api/branches/{branchId}/export/daily": ["date", "format"],
      "/api/branches/{branchId}/export/range": ["from", "to", "format"],
      "/api/branches/{branchId}/export/monthly": ["year", "month", "format"],
      "/api/branches/{branchId}/export/all-time": ["format"],
      "/api/branches/{branchId}/inventory/movements": ["date", "threshold"],
    };
    const queryNames = [...new Set([
      ...(method === "get" ? knownQueries[path] || [] : []),
      ...[...registration.source.matchAll(/(?:queryParam|uuidFromQuery)\s*\(\s*["']([^"']+)["']/g)].map((match) => match[1]),
    ])];
    for (const name of queryNames) {
      if (!operation.parameters.some((parameter) => parameter.in === "query" && parameter.name === name)) {
        operation.parameters.push({ name, in: "query", required: false, schema: { type: "string" } });
      }
    }
    for (const status of registration.source.matchAll(/HttpStatus\.(\w+)/g)) {
      const statusCode = { OK: "200", CREATED: "201", NO_CONTENT: "204", BAD_REQUEST: "400", UNAUTHORIZED: "401", FORBIDDEN: "403", NOT_FOUND: "404", CONFLICT: "409", UNPROCESSABLE_CONTENT: "422", TOO_MANY_REQUESTS: "429", SERVICE_UNAVAILABLE: "503" }[status[1]];
      if (!statusCode) continue;
      operation.responses[statusCode] ??= { description: status[1].replaceAll("_", " ") };
    }
    for (const [, name] of path.matchAll(/\{([^}]+)}/g)) {
      if (!operation.parameters.some((parameter) => parameter.in === "path" && parameter.name === name)) {
        operation.parameters.push({ name, in: "path", required: true, schema: { type: "string" } });
      }
    }
  }
}

for (const [name, schema] of Object.entries(dtoSchemas)) spec.components.schemas[name] ??= schema;

const operationIds = new Set();
for (const methods of Object.values(spec.paths ?? {})) {
  for (const [method, operation] of Object.entries(methods)) {
    if (!operation.operationId) continue;
    const base = operation.operationId;
    let candidate = base;
    let suffix = 2;
    while (operationIds.has(candidate)) candidate = `${base}_${method}_${suffix++}`;
    operation.operationId = candidate;
    operationIds.add(candidate);
  }
}

fs.mkdirSync(path.dirname(path.resolve(targetPath)), { recursive: true });
fs.writeFileSync(targetPath, `${JSON.stringify(spec, null, 2)}\n`);
