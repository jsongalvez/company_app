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
const serviceSources = fs.readdirSync(new URL("../backend/src/main/kotlin/com/companyb/companyapp/service/", import.meta.url), { withFileTypes: true })
  .filter((entry) => entry.isFile() && entry.name.endsWith(".kt"))
  .map((entry) => ({ name: entry.name, source: fs.readFileSync(new URL(entry.name, new URL("../backend/src/main/kotlin/com/companyb/companyapp/service/", import.meta.url)), "utf8") }));

function withoutComments(source) {
  let result = "";
  let index = 0;
  let quote = null;
  while (index < source.length) {
    if (!quote && source.startsWith("//", index)) {
      const end = source.indexOf("\n", index);
      const stop = end < 0 ? source.length : end;
      result += " ".repeat(stop - index);
      index = stop;
      continue;
    }
    if (!quote && source.startsWith("/*", index)) {
      const end = source.indexOf("*/", index + 2);
      const stop = end < 0 ? source.length : end + 2;
      result += source.slice(index, stop).replace(/[^\n]/g, " ");
      index = stop;
      continue;
    }
    const character = source[index];
    result += character;
    if (character === '"' && source[index - 1] !== "\\") quote = quote ? null : '"';
    index++;
  }
  return result;
}

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

function annotationMetadata(source, file) {
  const scanSource = withoutComments(source);
  const result = [];
  let cursor = 0;
  while (true) {
    const start = scanSource.indexOf("@OpenApi", cursor);
    if (start < 0) return result;
    const open = scanSource.indexOf("(", start);
    if (open < 0) throw new Error(`Unclosed OpenApi annotation in ${file}`);
    const annotation = balancedDelimited(scanSource, open, "(", ")");
    if (!annotation.endsWith(")")) throw new Error(`Unclosed OpenApi annotation in ${file}`);
    const body = annotation.slice(1, -1);
    const pathValue = body.match(/\bpath\s*=\s*"((?:[^"\\]|\\.)*)"/)?.[1];
    const methods = [...(body.match(/\bmethods\s*=\s*\[([\s\S]*?)\]/)?.[1] || "").matchAll(/HttpMethod\.(GET|POST|PATCH|DELETE)/g)].map((match) => match[1].toLowerCase());
    if (!pathValue || methods.length === 0) throw new Error(`Incomplete OpenApi annotation in ${file}`);
    const owner = source.slice(start).match(/(?:object|class)\s+(\w+)\s*\{/)?.[1];
    if (!owner) throw new Error(`OpenApi annotation is not owned by a route object in ${file}`);
    const operationId = body.match(/\boperationId\s*=\s*"([^"]+)"/)?.[1];
    if (!operationId) throw new Error(`OpenApi annotation has no operationId in ${file}`);
    result.push({ path: pathValue, methods, operationId, owner, file, source: source.slice(start, open + annotation.length) });
    cursor = open + annotation.length;
  }
}

function functionBody(source, handler) {
  const start = source.search(new RegExp(`(?:fun|private fun|internal fun)\\s+(?:[^\\s(]+\\.)?${handler}\\s*\\(`));
  return start < 0 ? "" : balancedBlock(source, start);
}

function handlerSource(source, handler, routeIndex) {
  const lambda = source.indexOf("->", routeIndex);
  const callOpen = source.indexOf("(", routeIndex);
  const call = callOpen < 0 ? "" : balancedDelimited(source, callOpen, "(", ")");
  const open = handler ? -1 : lambda < 0 ? -1 : source.indexOf("{", routeIndex + call.length + (callOpen - routeIndex));
  const nextRoute = source.indexOf("\n        config.routes.", routeIndex + 1);
  const routeWindow = source.slice(routeIndex, nextRoute < 0 ? source.length : nextRoute);
  const lambdaBody = open < 0 ? "" : source.slice(open, nextRoute < 0 ? source.length : nextRoute);
  const initial = handler ? functionBody(source, handler) : open < 0 ? routeWindow : `${balancedBlock(source, open)}\n${lambdaBody}\n${routeWindow}`;
  const included = new Set();
  let result = initial;
  for (let pass = 0; pass < 3; pass++) {
    for (const match of result.matchAll(/\b([a-zA-Z_]\w*)\s*\(/g)) {
      const name = match[1];
      if (included.has(name) || ["if", "when", "runCatching", "context", "json", "status"].includes(name)) continue;
      const helper = functionBody(source, name);
      if (helper) {
        included.add(name);
        result += `\n${helper}`;
      }
    }
  }
  return result;
}
function enclosingOwner(source, index) {
  const owners = [...source.slice(0, index).matchAll(/(?:object|class)\s+(\w+)\s*\{/g)];
  return owners.at(-1)?.[1] || null;
}
function serviceBehavior(source) {
  let result = "";
  for (const match of source.matchAll(/\b(\w+Service)\.(\w+)\s*\(/g)) {
    for (const service of serviceSources) {
      if (service.source.includes(`fun ${match[2]}(`)) result += `\n${functionBody(service.source, match[2])}`;
    }
  }
  return result;
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
  throw new Error(`Unsupported Kotlin DTO type: ${type}`);
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
  return [name, { type: "object", additionalProperties: false, properties: Object.fromEntries(properties.map((property) => [property.name, typeSchema(property.type)])), ...(required.length ? { required } : {}) }];
}));

function registrations() {
  const result = [];
  for (const { name, source } of routeSources) {
    const scanSource = withoutComments(source);
    for (const match of scanSource.matchAll(/(?:config|context)\.routes\.(get|post|patch|delete)\(\s*("(?:[^"\\]|\\.)*"|\$[A-Z0-9_]+)[\s\S]*?(?:::([A-Za-z0-9_]+)|\{\s*context\s*->)/g)) {
      const pathValue = match[2].replace(/\$([A-Z0-9_]+)/g, (_, constant) => scanSource.match(new RegExp(`const val ${constant}\\s*=\\s*"([^"]+)"`))?.[1] || constant.toLowerCase().replace(/_PARAM$/, ""));
      const routePath = pathValue.replace(/"/g, "");
      const callStart = source.indexOf("(", match.index);
      const registration = `${source.slice(match.index, callStart)}${balancedDelimited(source, callStart, "(", ")")}`;
      result.push({
        method: match[1],
        path: routePath,
        file: name,
        handler: match[3] || null,
        registration: registration.trim(),
        source: handlerSource(source, match[3], match.index),
        owner: enclosingOwner(source, match.index),
        fileSource: source,
      });
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
const annotationByRoute = new Map();
for (const { name, source } of routeSources) for (const annotation of annotationMetadata(source, name)) {
  for (const method of annotation.methods) {
    const key = `${method} ${annotation.path}`;
    if (annotationByRoute.has(key)) throw new Error(`Duplicate source OpenApi annotation: ${key}`);
    annotationByRoute.set(key, annotation);
  }
}

const statusNames = { OK: "200", CREATED: "201", NO_CONTENT: "204", BAD_REQUEST: "400", UNAUTHORIZED: "401", FORBIDDEN: "403", NOT_FOUND: "404", CONFLICT: "409", UNPROCESSABLE_CONTENT: "422", TOO_MANY_REQUESTS: "429", SERVICE_UNAVAILABLE: "503" };
const responseOverrides = {
  audit_log: ["AuditLogEntryResponse", true], audit_log_flagged: ["AuditLogEntryResponse", true], audit_log_tables: ["AuditLogTableResponse", true], audit_log_acknowledge: ["AuditLogEntryResponse", false],
  branch_day_users: ["BranchDayUserResponse", true], daily_summaries: ["DailySalesSummaryResponse", true], daily_summary: ["DailySalesSummaryResponse", false],
  branch_inventory_get: ["BranchInventoryResponse", true], inventory_low_stock: ["BranchInventoryResponse", true], inventory_movements: ["InventoryMovementResponse", true], inventory_movement: ["InventoryMovementResponse", false], inventory_restock: ["InventoryMovementResponse", false],
  relief_candidates: ["ReliefCandidateResponse", true], branch_remittance_days: ["RemittanceDayPickerEntryResponse", true], branch_remittance_product_sales: ["RemittanceProductSalePickerEntryResponse", true], branch_remittance_sessions: ["RemittanceSessionPickerEntryResponse", true],
  commission_inclusions: ["CommissionInclusionResponse", false], commission_splits: ["CommissionSplitResponse", true], commission_recalculate: ["CommissionSplitResponse", true], compensation_create: ["CompensationResponse", false], compensation_update: ["CompensationResponse", false], compensations: ["CompensationResponse", true], concerns: ["ConcernResponse", true],
  clients_get: ["ClientResponse", true], product_patch: ["ProductResponse", false], me_branches: ["MeBranchResponse", true], me_capabilities: ["UserCapabilityResponse", true], remittances_get: ["RemittanceResponse", true], remittances_post: ["RemittanceResponse", false], remittance_get: ["RemittanceDetailResponse", false], remittance_patch: ["RemittanceResponse", false], remittance_undo: ["RemittanceResponse", false], remittance_day_breakdowns: ["RemittanceDayBreakdownResponse", false], remittance_day_breakdown_delete: ["RemittanceDayBreakdownResponse", false], remittance_drift: ["RemittanceDriftResponse", false], remittance_lines: ["RemittanceLineResponse", false], remittance_line_delete: ["RemittanceLineResponse", false], remittance_submit: ["RemittanceSubmitResponse", false],
  session: ["SessionResponse", false], session_concerns_get: ["ConcernResponse", true], session_final_price: ["SessionResponse", false], session_practitioners: ["SessionPractitionerResponse", false], session_practitioner_patch: ["SessionPractitionerResponse", false], session_promote_concern: ["ConcernResponse", false], session_status: ["SessionResponse", false], session_type: ["SessionResponse", false], session_unvoid: ["SessionResponse", false], session_void: ["SessionResponse", false], users: ["UserSummaryResponse", true],
};
const responsePathOverrides = {
  "post /api/allowances": ["AllowanceResponse", false], "post /api/attendance/clock-in": ["ClockInResponse", false], "post /api/branches": ["BranchResponse", false], "post /api/branches/{branchId}/assignments": ["AssignmentResponse", false], "post /api/branches/{branchId}/inventory": ["BranchInventoryResponse", false], "post /api/branches/{branchId}/inventory/{productId}/movement": ["InventoryMovementResponse", false], "post /api/branches/{branchId}/inventory/{productId}/restock": ["InventoryMovementResponse", false], "post /api/branches/{branchId}/rates": ["RateResponse", false], "post /api/branches/{branchId}/relief-invites": ["ReliefInviteResponse", false], "post /api/clients": ["ClientResponse", false], "post /api/commission-inclusions": ["CommissionInclusionResponse", false], "post /api/compensation": ["CompensationResponse", false], "post /api/delegates": ["DelegateResponse", false], "post /api/expenses": ["ExpenseResponse", false], "post /api/product-categories": ["ProductCategoryResponse", false], "post /api/product-sales": ["ProductSaleResponse", false], "post /api/products": ["ProductResponse", false], "post /api/relief-access/request": ["ReliefAccessResponse", false], "post /api/remittances": ["RemittanceResponse", false], "post /api/remittances/{remittanceId}/day-breakdowns": ["RemittanceDayBreakdownResponse", false], "post /api/remittances/{remittanceId}/lines": ["RemittanceLineResponse", false], "post /api/sessions": ["SessionResponse", false], "post /api/sessions/{sessionId}/practitioners": ["SessionPractitionerResponse", false], "post /api/sessions/{sessionId}/promote-concern": ["ConcernResponse", false], "post /api/sessions/{sessionId}/void": ["SessionResponse", false],
};
const successStatusOverrides = {
  clients_get: "200",
  inventory_movements: "200",
  product_patch: "200",
  remittances_get: "200",
  remittance_undo: "200",
};

spec.info = { ...spec.info, title: "CompanyApp Backend API", version: "1.0.0" };
spec.components ??= {};
spec.components.securitySchemes ??= {};
spec.components.securitySchemes.BearerAuth = { type: "http", scheme: "bearer", bearerFormat: "JWT" };
spec.components.schemas ??= {};
spec.components.schemas.ErrorResponse = { type: "object", additionalProperties: false, required: ["error"], properties: { error: { type: "string" } } };
for (const [name, schema] of Object.entries(dtoSchemas)) spec.components.schemas[name] = schema;

for (const [routePath, methods] of Object.entries(spec.paths ?? {})) {
  for (const [method, operation] of Object.entries(methods)) {
    const registration = byRoute.get(`${method} ${routePath}`);
    if (!registration) throw new Error(`Generated operation is not bound to a route registration: ${method} ${routePath}`);
     operation["x-route-source"] = {
       file: registration.file,
       registration: registration.registration,
       owner: registration.owner,
       ...(registration.handler ? { handler: registration.handler } : {}),
     };
    const annotation = annotationByRoute.get(`${method} ${routePath}`);
    if (!annotation) throw new Error(`Generated operation has no source OpenApi annotation: ${method} ${routePath}`);
     if (annotation.owner !== registration.owner) throw new Error(`OpenAPI annotation owner does not match route owner: ${method} ${routePath}`);
     operation["x-openapi-source"] = { file: annotation.file, owner: annotation.owner, operationId: annotation.operationId, annotation: annotation.source };
     if (operation.operationId !== annotation.operationId) throw new Error(`Generated operationId does not match source annotation: ${method} ${routePath}`);
    operation.responses ??= {};
    const success = Object.keys(operation.responses).find((status) => /^2\d\d$/.test(status));
    let synthesizedSuccess = false;
    const forcedSuccess = successStatusOverrides[operation.operationId];
    if (!success && forcedSuccess) {
      operation.responses[forcedSuccess] = { description: forcedSuccess === "201" ? "Created" : "OK" };
      synthesizedSuccess = true;
    }
    if (!success && (registration.source.includes("context.json(") || registration.source.includes("context.result("))) {
      operation.responses["200"] = { description: "OK" };
      synthesizedSuccess = true;
    }
    if (routePath.includes("/export/")) {
      operation.responses["200"] = { description: "OK", content: { "application/octet-stream": { schema: { type: "string", format: "binary" } } } };
      operation.responses["400"] = { description: "Bad Request", content: { "application/json": { schema: { $ref: "#/components/schemas/ErrorResponse" } } } };
    }
    const body = registration.source.match(/(?:bodyAsClass|bodyIfPresent)\s*<\s*(\w+)\s*>/);
    if (body && dtoSchemas[body[1]]) operation.requestBody = { required: !registration.source.includes("bodyIfPresent"), content: { "application/json": { schema: { $ref: `#/components/schemas/${body[1]}` } } } };
    if (!body) delete operation.requestBody;
    const responseTypes = [...registration.source.matchAll(/(?:context\.json\s*\(\s*(\w+Response)\s*\(|toResponse\(\)\s*:\s*(\w+Response))/g)].flatMap((match) => [match[1], match[2]]).filter((name) => name && dtoSchemas[name]);
    const typedResponse = registration.source.match(/:\s*(List<)?(\w+Response)\s*=\s*[\s\S]*?context\.json\s*\(/);
    if (typedResponse?.[2] && dtoSchemas[typedResponse[2]]) responseTypes.push(typedResponse[2]);
    if (registration.source.includes("toResponse()")) {
      const declarations = [...registration.fileSource.matchAll(/toResponse\(\)\s*:\s*(\w+Response)/g)].map((match) => match[1]).filter((name) => dtoSchemas[name]);
      if (declarations.length === 1) responseTypes.push(declarations[0]);
    }
    const responseType = responseTypes[0];
    const override = responseOverrides[operation.operationId] || responsePathOverrides[`${method} ${routePath}`];
     if (!responseType && override && dtoSchemas[override[0]]) responseTypes.push(override[0]);
     const resolvedResponseType = responseTypes[0] || (override && dtoSchemas[override[0]] ? override[0] : undefined);
    const successStatuses = Object.keys(operation.responses).filter((status) => /^2\d\d$/.test(status) && status !== "204");
    if (resolvedResponseType && successStatuses.length) {
       const isArray = override?.[1] || typedResponse?.[1];
      const schema = isArray ? { type: "array", items: { $ref: `#/components/schemas/${resolvedResponseType}` } } : { $ref: `#/components/schemas/${resolvedResponseType}` };
      for (const status of successStatuses) operation.responses[status].content = { "application/json": { schema } };
    }
    const documentedSuccess = Object.entries(operation.responses).find(([status, response]) => /^2\d\d$/.test(status) && response.content)?.[1];
    if (documentedSuccess) for (const status of successStatuses) operation.responses[status].content ??= documentedSuccess.content;
    if (routePath === "/health") {
      const healthSchema = { type: "object", additionalProperties: false, required: ["status"], properties: { status: { type: "string", enum: ["UP", "DOWN"] }, error: { type: "string" } } };
      for (const status of ["200", "503"]) operation.responses[status].content = { "application/json": { schema: healthSchema } };
    }
     const behaviorSource = `${registration.source}\n${serviceBehavior(registration.source)}`;
     const explicitStatuses = [...behaviorSource.matchAll(/HttpStatus\.(\w+)/g)].map((match) => match[1]);
     for (const match of behaviorSource.matchAll(/(?:BadRequestResponse|ValidationException)/g)) explicitStatuses.push("BAD_REQUEST");
     for (const match of behaviorSource.matchAll(/(?:NotFoundException|NotFoundResponse)/g)) explicitStatuses.push("NOT_FOUND");
     for (const match of behaviorSource.matchAll(/(?:ConflictException|ConflictResponse)/g)) explicitStatuses.push("CONFLICT");
     for (const match of behaviorSource.matchAll(/(?:ForbiddenException|ForbiddenResponse)/g)) explicitStatuses.push("FORBIDDEN");
     // A route can return both a success result and domain errors. Keep synthesized success
     // metadata when source evidence also contains error outcomes.
    for (const status of explicitStatuses) {
      const code = statusNames[status];
      if (code) operation.responses[code] ??= { description: status.replaceAll("_", " "), ...(code.startsWith("4") ? { content: { "application/json": { schema: { $ref: "#/components/schemas/ErrorResponse" } } } } : {}) };
    }
    for (const [code, response] of Object.entries(operation.responses)) {
      if (/^[45]\d\d$/.test(code)) response.content ??= { "application/json": { schema: { $ref: "#/components/schemas/ErrorResponse" } } };
    }
  if (routePath.startsWith("/api/")) {
       operation.security ??= [{ BearerAuth: [] }];
       operation.responses["401"] ??= { description: "Unauthorized", content: { "application/json": { schema: { $ref: "#/components/schemas/ErrorResponse" } } } };
    }
    operation.parameters = (operation.parameters || []).filter((parameter) => parameter.in === "path");
     const queries = {};
     for (const match of registration.source.matchAll(/queryParam\s*\(\s*["']([^"']+)["']/g)) {
       const required = new RegExp(`queryParam\\s*\\(\\s*["']${match[1]}["']\\s*\\)\\s*\\?:`).test(registration.source);
       queries[match[1]] ??= [required, { type: "string" }];
     }
     for (const match of registration.source.matchAll(/uuidFromQuery\s*\(\s*["']([^"']+)["']/g)) queries[match[1]] ??= [true, { type: "string", format: "uuid" }];
     for (const match of registration.source.matchAll(/parseRequiredDate\s*\(\s*[^,]+queryParam\s*\(\s*["']([^"']+)["']/g)) queries[match[1]] = [true, { type: "string", format: "date" }];
     for (const match of registration.source.matchAll(/parseOptionalDate\s*\(\s*[^,]+queryParam\s*\(\s*["']([^"']+)["']/g)) queries[match[1]] = [false, { type: "string", format: "date" }];
     for (const match of registration.source.matchAll(/parseRequiredDate\s*\(\s*\w+\s*,\s*["']([^"']+)["']/g)) queries[match[1]] = [true, { type: "string", format: "date" }];
     for (const match of registration.source.matchAll(/parseRequiredInt\s*\(\s*\w+\s*,\s*["']([^"']+)["']/g)) queries[match[1]] = [true, { type: "integer" }];
     for (const match of registration.source.matchAll(/queryParam\s*\(\s*["']([^"']+)["']\s*\)\?\.toIntOrNull\(\)/g)) queries[match[1]] = [false, { type: "integer" }];
     for (const match of registration.source.matchAll(/(?:val|var)\s+(\w+)(?:Param)?\s*=\s*(?:\w+\.)?queryParam\s*\(\s*["']([^"']+)["']\s*\)/g)) {
       const type = /(?:year|month|limit|threshold)/i.test(match[1]) ? { type: "integer" } : /date|from|to/i.test(match[1]) ? { type: "string", format: "date" } : { type: "string" };
       const required = new RegExp(`${match[1]}(?:Param)?\\s*=\\s*queryParam[\\s\\S]{0,80}\\?:`).test(registration.source);
       queries[match[2]] = [required, type];
     }
     if (registration.source.includes('"csv"') && registration.source.includes('"pdf"') && queries.format) queries.format[1] = { type: "string", enum: ["csv", "pdf"] };
     for (const match of registration.source.matchAll(/queryParam\s*\(\s*["']([^"']+)["']\s*\)\s*\?:\s*throw/g)) queries[match[1]] = [true, queries[match[1]]?.[1] || { type: "string" }];
    for (const [name, [required, schema]] of Object.entries(queries)) operation.parameters.push({ name, in: "query", required, schema });
    for (const [, name] of routePath.matchAll(/\{([^}]+)\}/g)) if (!operation.parameters.some((parameter) => parameter.in === "path" && parameter.name === name)) operation.parameters.push({ name, in: "path", required: true, schema: { type: "string", format: "uuid" } });
  }
}
const operationIds = new Set();
for (const methods of Object.values(spec.paths ?? {})) for (const [method, operation] of Object.entries(methods)) {
   if (!operation.operationId) throw new Error(`Missing operationId for ${method}`);
   if (operationIds.has(operation.operationId)) throw new Error(`Duplicate generated operationId: ${operation.operationId}`);
  operationIds.add(operation.operationId);
}
fs.mkdirSync(path.dirname(path.resolve(targetPath)), { recursive: true });
fs.writeFileSync(targetPath, `${JSON.stringify(spec, null, 2)}\n`);
