import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { balancedDelimited, enclosingOwner, sourceAnnotations, splitTopLevel, withoutComments } from "./openapi-source-parser.mjs";

const [sourcePath, targetPath] = process.argv.slice(2);
if (!sourcePath || !targetPath) throw new Error("source and target paths required");
const spec = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
const routeContractPath = process.env.OPENAPI_TEST_MODE === "1" && process.env.OPENAPI_ROUTE_CONTRACT_PATH
  ? process.env.OPENAPI_ROUTE_CONTRACT_PATH
  : new URL("./openapi-route-contract.json", import.meta.url);
const routeContract = JSON.parse(fs.readFileSync(routeContractPath, "utf8"));
const routeDir = new URL("../backend/src/main/kotlin/com/companyb/companyapp/api/routes/", import.meta.url);
const dtoDir = new URL("../shared/src/commonMain/kotlin/com/companyb/companyapp/dto/", import.meta.url);
const apiRoutesSource = fs.readFileSync(new URL("../shared/src/commonMain/kotlin/com/companyb/companyapp/api/ApiRoutes.kt", import.meta.url), "utf8");
const apiRouteConstants = new Map([...apiRoutesSource.matchAll(/const val (\w+)\s*=\s*"([^"]*)"/g)].map((match) => [match[1], match[2]]));
function resolveApiRoute(value) {
  let resolved = value;
  for (let pass = 0; pass < 10; pass++) {
    const next = resolved.replace(/\$([A-Z][A-Z0-9_]*)/g, (_, name) => apiRouteConstants.get(name) || `$${name}`);
    if (next === resolved) return next;
    resolved = next;
  }
  return resolved;
}
const routeSources = fs.readdirSync(routeDir).filter((name) => name.endsWith("Routes.kt")).map((name) => ({
   name,
   source: fs.readFileSync(new URL(name, routeDir), "utf8"),
}));
function kotlinSources(directory) {
  const result = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = new URL(entry.isDirectory() ? `${entry.name}/` : entry.name, directory);
    if (entry.isDirectory()) result.push(...kotlinSources(file));
    else if (entry.name.endsWith(".kt")) result.push({ name: entry.name, source: fs.readFileSync(file, "utf8") });
  }
  return result;
}
const serviceSources = kotlinSources(new URL("../backend/src/main/kotlin/com/companyb/companyapp/service/", import.meta.url));
const mappingDir = new URL("../backend/src/main/kotlin/com/companyb/companyapp/api/mapping/", import.meta.url);
const mappingSources = fs.readdirSync(mappingDir).filter((name) => name.endsWith(".kt")).map((name) => ({ name, source: fs.readFileSync(new URL(name, mappingDir), "utf8") }));
const responseExtensions = new Map();
function responseExtension(type, method) {
  const key = `${type}.${method}`;
  const candidates = responseExtensions.get(key) || [];
  return candidates.length === 1 ? candidates[0] : undefined;
}
for (const { source } of [...routeSources, ...serviceSources, ...mappingSources]) {
  for (const match of source.matchAll(/(?:(?:private|internal|public)\s+)?fun\s+(\w+)\.(\w+)\([\s\S]{0,300}?\)\s*:\s*(\w+Response)/g)) {
    const key = `${match[1]}.${match[2]}`;
    const candidates = responseExtensions.get(key) || [];
    if (!candidates.includes(match[3])) candidates.push(match[3]);
    responseExtensions.set(key, candidates);
  }
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

function annotationMetadata(source, file) {
  return sourceAnnotations(source, file, (value) => resolveApiRoute(apiRouteConstants.get(value) || value))
    .map((annotation) => ({ ...annotation, file }));
}

function functionBody(source, handler) {
  const start = source.indexOf(`fun ${handler}(`) >= 0 ? source.indexOf(`fun ${handler}(`) : source.search(new RegExp(`(?:fun|private fun|internal fun)\\s+(?:[^\\s(]+\\.)?${handler}\\s*\\(`));
  const declarations = [...source.matchAll(new RegExp(`(?:^|\\n)\\s*(?:(?:private|internal|public)\\s+)?fun\\s+${handler}\\s*\\(`, "g"))];
  if (declarations.length !== 1) return "";
  return balancedBlock(source, declarations[0].index);
}

function directHandlerSource(source, handler, routeIndex) {
  const callOpen = source.indexOf("(", routeIndex);
  const call = callOpen < 0 ? "" : balancedDelimited(source, callOpen, "(", ")");
  const lambda = source.indexOf("->", callOpen + call.length);
  const open = handler ? -1 : lambda < 0 ? -1 : source.lastIndexOf("{", lambda);
  if (handler) {
    const selected = functionBody(source, handler);
    if (!selected) throw new Error(`Named route handler is not unique: ${handler}`);
    return selected;
  }
  return open < 0 ? "" : balancedBlock(source, open);
}
function handlerSource(source, handler, routeIndex) {
  const initial = directHandlerSource(source, handler, routeIndex);
  const included = new Set();
  let result = initial;
  for (let pass = 0; pass < 3; pass++) {
    for (const match of result.matchAll(/\b([a-zA-Z_]\w*)\s*\(/g)) {
      const name = match[1];
       if (included.has(name) || name.startsWith("to") || name.startsWith("handle") || ["if", "when", "runCatching", "context", "json", "status"].includes(name)) continue;
      const helper = functionBody(source, name);
      if (helper) {
        included.add(name);
        result += `\n${helper}`;
      }
    }
  }
  return result;
}
function sourceHash(source) {
  return crypto.createHash("sha256").update(source).digest("hex");
}
function serviceBehavior(source) {
  let result = "";
  for (const match of source.matchAll(/\b(\w+Service)\.(\w+)\s*\(/g)) {
    const service = serviceSources.find((candidate) => candidate.name === `${match[1]}.kt`);
    if (!service) continue;
    const body = functionBody(service.source, match[2]);
    if (body) result += `\n${body}`;
  }
  return result;
}
function responseArguments(source) {
  const result = [];
  for (const match of source.matchAll(/context\.json\s*\(/g)) {
    result.push(balancedDelimited(source, match.index + match[0].length - 1, "(", ")").slice(1, -1));
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
    for (const match of scanSource.matchAll(/(?:config|context)\.routes\.(get|post|patch|delete|put)\(\s*("(?:[^"\\]|\\.)*"|\$[A-Z0-9_]+|ApiRoutes\.\w+)[\s\S]*?(?:::([A-Za-z0-9_]+)|\{\s*context\s*->)/g)) {
      const pathValue = match[2].startsWith("ApiRoutes.")
        ? resolveApiRoute(apiRouteConstants.get(match[2].slice("ApiRoutes.".length)) || "")
        : match[2].replace(/\$([A-Z0-9_]+)/g, (_, constant) => scanSource.match(new RegExp(`const val ${constant}\\s*=\\s*"([^"]+)"`))?.[1] || constant.toLowerCase().replace(/_PARAM$/, ""));
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
         selectedHandlerSource: directHandlerSource(source, match[3] || null, match.index),
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
     if (!registration.source.trim()) throw new Error(`Route has no selected handler source: ${method} ${routePath}`);
      operation["x-route-source"] = {
       file: registration.file,
       registration: registration.registration,
       owner: registration.owner,
       ...(registration.handler ? { handler: registration.handler } : {}),
        selectedHandlerSource: registration.selectedHandlerSource,
        selectedHandlerHash: sourceHash(registration.selectedHandlerSource),
        selectedHandlerStart: registration.fileSource.indexOf(registration.selectedHandlerSource),
        selectedHandlerEnd: registration.fileSource.indexOf(registration.selectedHandlerSource) + registration.selectedHandlerSource.length,
        key: `${method} ${routePath}`,
     };
    const annotation = annotationByRoute.get(`${method} ${routePath}`);
    if (!annotation) throw new Error(`Generated operation has no source OpenApi annotation: ${method} ${routePath}`);
     if (annotation.owner !== registration.owner) throw new Error(`OpenAPI annotation owner does not match route owner: ${method} ${routePath}`);
      operation["x-openapi-source"] = { file: annotation.file, owner: annotation.owner, operationId: annotation.operationId, annotation: annotation.source, key: `${method} ${routePath}` };
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
      const responseArgs = responseArguments(registration.source);
      const responseTypes = responseArgs
        .flatMap((argument) => [...argument.matchAll(/\b(\w+Response)\s*\(/g)].map((match) => match[1]))
        .filter((name) => name !== "ErrorResponse" && dtoSchemas[name]);
      const directResponseTypes = [...new Set(responseTypes)];
      for (const argument of responseArgs) {
        const mappedVariable = argument.match(/\b(\w+)\.map\s*\{/i)?.[1];
        if (!mappedVariable) continue;
        const mapperDeclarations = [...registration.fileSource.matchAll(/(?:(?:private|internal|public)\s+)?fun\s+(\w+)\.toResponse\s*\([^)]*\)\s*:\s*(\w+Response)/g)]
          .filter((match) => dtoSchemas[match[2]]);
        if (mapperDeclarations.length === 1) responseTypes.push(mapperDeclarations[0][2]);
        let sourceVariable = mappedVariable;
        for (let depth = 0; depth < 3; depth++) {
          const assignment = registration.source.match(new RegExp(`\\bval\\s+${sourceVariable}\\s*=([\\s\\S]*?)(?=\\n\\s*(?:val|context\\.|return|$))`));
          const serviceCall = assignment?.[1]?.match(/\b(\w+Service)\.(\w+)\s*\(/);
          if (serviceCall) {
            const service = serviceSources.find((candidate) => candidate.name === `${serviceCall[1]}.kt`);
            const declaration = service?.source.match(new RegExp(`fun\\s+${serviceCall[2]}\\s*\\(`));
            if (service && declaration) {
              const open = declaration.index + declaration[0].length - 1;
              const parameters = balancedDelimited(service.source, open, "(", ")");
              const returnType = service.source.slice(open + parameters.length).match(/^\s*:\s*(?:List<|Set<)?(\w+)/)?.[1];
              const responseType = returnType && responseExtension(returnType, "toResponse");
              if (responseType && dtoSchemas[responseType]) responseTypes.push(responseType);
            }
            break;
          }
          const upstream = assignment && [...assignment[1].matchAll(/\b(\w+)\b/g)]
            .map((match) => match[1])
            .find((name) => name !== sourceVariable && new RegExp(`\\bval\\s+${name}\\s*=`).test(registration.source));
          if (!upstream || upstream[1] === sourceVariable) break;
          sourceVariable = upstream[1];
        }
      }
     const responseArray = responseArgs.some((argument) => /\.map\s*\{|\bList\s*</.test(argument));
      for (const argument of responseArgs) {
        for (const match of argument.matchAll(/(?:^|\.)(\w+)\.(toResponse|to\w+Response)\s*\(/g)) {
          const receiver = match[1][0].toUpperCase() + match[1].slice(1);
          const responseType = responseExtension(receiver, match[2]);
          if (responseType && dtoSchemas[responseType]) responseTypes.unshift(responseType);
        }
      }
      for (const declaration of registration.source.matchAll(/\b(?:val|var)\s+(\w+)\s*:\s*((?:List|Set)<)?([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)(?:>)?\s*=/g)) {
        const typeName = declaration[3].split(".").at(-1);
        const responseMethod = responseArgs
          .map((argument) => new RegExp(`\\b${declaration[1]}\\.(to\\w+Response)\\s*\\(`).exec(argument)?.[1])
          .find(Boolean);
        if (!responseMethod) continue;
        const responseType = responseExtension(typeName, responseMethod);
        if (responseType && dtoSchemas[responseType]) responseTypes.unshift(responseType);
      }
     const typedResponse = registration.source.match(/:\s*(?:List<\s*)?(\w+Response)\s*(>)?\s*=\s*[\s\S]*?context\.json\s*\(/);
     if (typedResponse?.[1] && dtoSchemas[typedResponse[1]]) responseTypes.push(typedResponse[1]);
      for (const assignment of registration.source.matchAll(/\bval\s+(\w+)\s*=\s*(\w+Service)\.(\w+)\s*\(/g)) {
         if (!responseArgs.some((argument) => new RegExp(`\\b${assignment[1]}\\b`).test(argument))) continue;
        const service = serviceSources.find((candidate) => candidate.name === `${assignment[2]}.kt`);
        const declaration = service?.source.match(new RegExp(`fun\\s+${assignment[3]}\\s*\\(`));
        if (!service || !declaration) continue;
        const open = declaration.index + declaration[0].length - 1;
        const parameters = balancedDelimited(service.source, open, "(", ")");
         const returnType = service.source.slice(open + parameters.length).match(/^\s*:\s*(?:List<|Set<)?(\w+)/)?.[1];
         const mapper = responseArgs
           .filter((argument) => new RegExp(`\\b${assignment[1]}\\b`).test(argument))
          .map((argument) => argument.match(/\.(toResponse|to\w+Response)\s*\(/)?.[1])
           .find(Boolean);
         const responseType = returnType?.endsWith("Response")
           ? returnType
           : returnType && responseExtension(returnType, mapper || "toResponse");
        if (responseType && dtoSchemas[responseType]) responseTypes.push(responseType);
      }
      for (const argument of responseArgs) for (const call of argument.matchAll(/\b(\w+Service)\.(\w+)\s*\(/g)) {
        const service = serviceSources.find((candidate) => candidate.name === `${call[1]}.kt`);
        const declaration = service?.source.match(new RegExp(`fun\\s+${call[2]}\\s*\\(`));
        if (!service || !declaration) continue;
        const open = declaration.index + declaration[0].length - 1;
        const parameters = balancedDelimited(service.source, open, "(", ")");
        const returnType = service.source.slice(open + parameters.length).match(/^\s*:\s*(?:List<|Set<)?(\w+)/)?.[1];
         const responseType = returnType?.endsWith("Response") ? returnType : returnType && responseExtension(returnType, "toResponse");
        if (responseType && dtoSchemas[responseType]) responseTypes.push(responseType);
      }
      if (responseArgs.some((argument) => /\bmapDashboardSession\s*\(/.test(argument))) {
        responseTypes.length = 0;
        responseTypes.push("DashboardSessionResponse");
      }
      if (responseArgs.some((argument) => /\.session\.toResponse\s*\(/.test(argument))) {
        responseTypes.length = 0;
        responseTypes.push("SessionResponse");
      }
      const directTypedMapper = registration.selectedHandlerSource.match(/\b(?:val|var)\s+\w+\s*:\s*(?:[\w.]+\.)?(\w+)\s*=\s*[\s\S]*?\.toResponse\s*\(\)/);
      if (directTypedMapper && dtoSchemas[`${directTypedMapper[1]}Response`]) responseTypes.push(`${directTypedMapper[1]}Response`);
      const listMapper = registration.selectedHandlerSource.match(/\b(?:val|var)\s+\w+\s*:\s*List<\s*(\w+)\s*>\s*=[\s\S]*?\.map\s*\{[\s\S]*?\.((?:to)\w+Response)\s*\(\)/);
      const listResponseType = listMapper && responseExtension(listMapper[1], listMapper[2]);
      if (listResponseType && dtoSchemas[listResponseType]) responseTypes.push(listResponseType);
      for (const match of responseArgs.flatMap((argument) => [...argument.matchAll(/\b(\w+)\.(toResponse|to\w+Response)\s*\(/g)])) {
        const receiverSuffix = match[1][0].toUpperCase() + match[1].slice(1);
        const candidates = [...registration.fileSource.matchAll(new RegExp(`fun\\s+(\\w*${receiverSuffix})\\.${match[2]}\\([^)]*\\)\\s*:\\s*(\\w+Response)`, "g"))];
        if (candidates.length === 1 && dtoSchemas[candidates[0][2]]) responseTypes.push(candidates[0][2]);
      }
      for (const call of registration.selectedHandlerSource.matchAll(/\.(\w+)\.(toResponse)\s*\(\)/g)) {
        const candidates = [...registration.fileSource.matchAll(/fun\s+(\w+)\.toResponse\s*\(\)\s*:\s*(\w+Response)/g)]
          .filter((candidate) => candidate[1].toLowerCase().endsWith(call[1].toLowerCase()));
        if (candidates.length === 1 && dtoSchemas[candidates[0][2]]) responseTypes.push(candidates[0][2]);
      }
      const distinctResponseTypes = [...new Set(responseTypes)];
      const resolvedResponseType = directResponseTypes.length === 1
        ? directResponseTypes[0]
        : distinctResponseTypes.length === 1 ? distinctResponseTypes[0] : undefined;
     const explicitStatuses = [...registration.source.matchAll(/HttpStatus\.(\w+)/g)].map((match) => statusNames[match[1]]).filter(Boolean);
      if (explicitStatuses.length) {
       for (const status of Object.keys(operation.responses)) {
         if (/^2\d\d$/.test(status) && !explicitStatuses.includes(status)) delete operation.responses[status];
      }
      if (registration.source.includes("HttpStatus.CREATED") && registration.source.includes("return@post")) delete operation.responses["201"]?.content;
       for (const status of explicitStatuses) operation.responses[status] ??= { description: status === "204" ? "No Content" : status === "201" ? "Created" : "OK" };
     }
      const successStatuses = Object.keys(operation.responses).filter((status) => /^2\d\d$/.test(status) && status !== "204");
      const explicitlyBodylessSuccess =
        registration.source.includes("HttpStatus.NO_CONTENT") ||
        operation.operationId === "auth_register" ||
        operation.operationId === "auth_logout" ||
        (method === "post" && routePath === "/api/branches/{branchId}/inventory") ||
        routePath.includes("/export/");
      if (routePath !== "/health" && successStatuses.length > 0 && resolvedResponseType === undefined && !explicitlyBodylessSuccess) {
        throw new Error(`Response contract is missing or ambiguous: ${method.toUpperCase()} ${routePath}`);
      }
    if (resolvedResponseType && successStatuses.length) {
        const isArray = typedResponse?.[1] || responseArray;
      const schema = isArray ? { type: "array", items: { $ref: `#/components/schemas/${resolvedResponseType}` } } : { $ref: `#/components/schemas/${resolvedResponseType}` };
      for (const status of successStatuses) operation.responses[status].content = { "application/json": { schema } };
    }
      const documentedSuccess = Object.entries(operation.responses).find(([status, response]) => /^2\d\d$/.test(status) && response.content)?.[1];
      if (resolvedResponseType && documentedSuccess) for (const status of successStatuses) operation.responses[status].content ??= documentedSuccess.content;
    if (routePath === "/health") {
      const healthSchema = { type: "object", additionalProperties: false, required: ["status"], properties: { status: { type: "string", enum: ["UP", "DOWN"] }, error: { type: "string" } } };
      for (const status of ["200", "503"]) operation.responses[status].content = { "application/json": { schema: healthSchema } };
    }
     const behaviorSource = `${registration.source}\n${serviceBehavior(registration.source)}`;
      const behaviorStatuses = [...behaviorSource.matchAll(/HttpStatus\.(\w+)/g)].map((match) => match[1]);
      for (const match of behaviorSource.matchAll(/(?:BadRequestResponse|ValidationException)/g)) behaviorStatuses.push("BAD_REQUEST");
      for (const match of behaviorSource.matchAll(/(?:NotFoundException|NotFoundResponse)/g)) behaviorStatuses.push("NOT_FOUND");
      for (const match of behaviorSource.matchAll(/(?:ConflictException|ConflictResponse)/g)) behaviorStatuses.push("CONFLICT");
      for (const match of behaviorSource.matchAll(/(?:ForbiddenException|ForbiddenResponse)/g)) behaviorStatuses.push("FORBIDDEN");
     // A route can return both a success result and domain errors. Keep synthesized success
     // metadata when source evidence also contains error outcomes.
     for (const status of behaviorStatuses) {
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
const contractRows = [];
for (const [routePath, methods] of Object.entries(spec.paths ?? {})) for (const [method, operation] of Object.entries(methods)) {
  contractRows.push([
    `${method} ${routePath}`,
    operation.operationId,
    operation["x-openapi-source"]?.annotation,
    operation["x-route-source"]?.registration,
    operation["x-route-source"]?.selectedHandlerSource,
  ]);
}
const contractFingerprint = sourceHash(JSON.stringify(contractRows.sort()));
if (contractFingerprint !== routeContract.fingerprint) {
  if (process.env.UPDATE_OPENAPI_ROUTE_CONTRACT === "1") {
    fs.writeFileSync(new URL("./openapi-route-contract.json", import.meta.url), `${JSON.stringify({ fingerprint: contractFingerprint }, null, 2)}\n`);
  } else {
    throw new Error("OpenAPI route contract fingerprint is stale");
  }
}
fs.mkdirSync(path.dirname(path.resolve(targetPath)), { recursive: true });
fs.writeFileSync(targetPath, `${JSON.stringify(spec, null, 2)}\n`);
