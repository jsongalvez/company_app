export function withoutComments(source) {
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

export function balancedDelimited(source, start, opening, closing) {
  const open = source.indexOf(opening, start);
  if (open < 0) throw new Error(`Unclosed delimiter at ${start}`);
  let depth = 0;
  let quoted = false;
  for (let index = open; index < source.length; index++) {
    if (source[index] === '"' && source[index - 1] !== "\\") quoted = !quoted;
    if (quoted) continue;
    if (source[index] === opening) depth++;
    if (source[index] === closing && --depth === 0) return source.slice(open, index + 1);
  }
  throw new Error(`Unclosed delimiter at ${start}`);
}

export function splitTopLevel(value) {
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

export function enclosingOwner(source, index) {
  const owners = [...source.slice(0, index).matchAll(/(?:object|class)\s+(\w+)\s*\{/g)];
  return owners.at(-1)?.[1] || source.slice(index).match(/(?:object|class)\s+(\w+)\s*\{/)?.[1] || null;
}

export function sourceAnnotations(source, file, resolvePath = (value) => value) {
  const result = [];
  const scanSource = withoutComments(source);
  let cursor = 0;
  while (true) {
    const start = scanSource.indexOf("@OpenApi", cursor);
    if (start < 0) return result;
    const open = scanSource.indexOf("(", start);
    if (open < 0) throw new Error(`Unclosed OpenApi annotation in ${file}`);
    const annotation = balancedDelimited(scanSource, open, "(", ")");
    const body = annotation.slice(1, -1);
    const pathMatch = body.match(/\bpath\s*=\s*(?:"((?:[^"\\]|\\.)*)"|ApiRoutes\.(\w+))/);
    const path = pathMatch ? resolvePath(pathMatch[1] || pathMatch[2]) : undefined;
    const methods = [...(body.match(/\bmethods\s*=\s*\[([\s\S]*?)\]/)?.[1] || "").matchAll(/HttpMethod\.(GET|POST|PATCH|DELETE)/g)].map((match) => match[1].toLowerCase());
    if (!path || methods.length === 0) throw new Error(`Incomplete OpenApi annotation in ${file}`);
    const owner = enclosingOwner(source, start);
    if (!owner) throw new Error(`OpenApi annotation is not owned by a route object in ${file}`);
    const operationId = body.match(/\boperationId\s*=\s*"([^"]+)"/)?.[1];
    if (!operationId) throw new Error(`OpenApi annotation has no operationId in ${file}`);
    result.push({ start, end: open + annotation.length, path, methods, owner, operationId, source: source.slice(start, open + annotation.length) });
    cursor = open + annotation.length;
  }
}
