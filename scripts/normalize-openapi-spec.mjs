import fs from "node:fs";

const [sourcePath, targetPath] = process.argv.slice(2);
if (!sourcePath || !targetPath) throw new Error("source and target paths required");

const spec = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
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
    operation.responses ??= {};
    operation.responses["200"] ??= { description: "OK" };
    if (["post", "put", "patch"].includes(method) && !operation.requestBody) {
      operation.requestBody = {
        required: true,
        content: { "application/json": { schema: { type: "object" } } },
      };
    }
    operation.parameters ??= [];
    const queryNames =
      path === "/api/clients" && method === "get" ? ["q"] :
      path === "/api/branches/{branchId}/daily-summary" && method === "get" ? ["date"] :
      path === "/api/branches/{branchId}/daily-summaries" && method === "get" ? ["cursor", "limit", "from", "to"] :
      path === "/api/branches/{branchId}/relief-candidates" && method === "get" ? ["q", "date"] :
      path === "/api/audit-log" && method === "get" ? ["tableName", "recordId"] :
      path === "/api/audit-log/entries" && method === "get" ?
        ["tableName", "action", "callerName", "dateFrom", "dateTo", "cursor", "limit"] :
      path === "/api/remittances" && method === "get" ? ["status"] :
      path.includes("/remittance-") && method === "get" ? ["from", "to"] :
      path === "/api/branches/{branchId}/monthly-summary" && method === "get" ? ["year", "month"] :
      path.includes("/export/") && method === "get" ? ["date", "from", "to", "year", "month", "format"] :
      path.includes("/inventory/movements") && method === "get" ? ["date", "threshold"] : [];
    for (const name of queryNames) {
      if (!operation.parameters.some((parameter) => parameter.in === "query" && parameter.name === name)) {
        operation.parameters.push({ name, in: "query", required: false, schema: { type: "string" } });
      }
    }
    for (const [, name] of path.matchAll(/\{([^}]+)}/g)) {
      if (!operation.parameters.some((parameter) => parameter.in === "path" && parameter.name === name)) {
        operation.parameters.push({ name, in: "path", required: true, schema: { type: "string" } });
      }
    }
  }
}

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

fs.mkdirSync(new URL(".", `file://${targetPath}`).pathname, { recursive: true });
fs.writeFileSync(targetPath, `${JSON.stringify(spec, null, 2)}\n`);
