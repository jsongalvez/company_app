import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { FAMILIES, key } from "../../src/lib/keys";
import pinned from "./fixtures/keys-f665552f.json";

/** Every loader's key at f665552f, before keys.ts existed, with each part written as X. */
const before = pinned as Record<string, string>;
const X = "X";
const builders = key as unknown as Record<string, (...a: unknown[]) => string>;
const full = (name: string) => builders[name](...Array.from({ length: builders[name].length }, () => X));

test("each key builder gives the key its loader had before", () => {
    for (const [name, k] of Object.entries(before)) {
        assert.ok(name in key, `keys.ts has no builder for loader ${name}`);
        assert.equal(full(name), k, name);
    }
});

test("every loader in data.ts takes its key from the builder of the same name", () => {
    const src = readFileSync("src/lib/data.ts", "utf8");
    const body = src.slice(src.indexOf("export const load = {"));
    const loaders = [...body.matchAll(/^ {4}(\w+): \(/gm)].map((m) => m[1]);
    assert.deepEqual(loaders.sort(), Object.keys(before).sort());
    for (const name of loaders) assert.match(body, new RegExp(`^ {4}${name}: \\([^)]*\\) =>\\s*L\\(key\\.${name}\\(`, "m"), name);
});

test("a family with no parts never matches a longer family's keys", () => {
    for (const f of FAMILIES) for (const g of FAMILIES) if (f !== g) assert.ok(!`${g}:`.startsWith(`${f}:`), `${f} vs ${g}`);
});

// A loader that unwraps with bare ok() records no reads, so every save would make it stale: each one goes through read(seen, ...).
test("every loader in data.ts notes the tables its answer read", () => {
    const src = readFileSync("src/lib/data.ts", "utf8");
    const body = src.slice(src.indexOf("export const load = {"), src.indexOf("export function warm"));
    assert.doesNotMatch(body, /\bok\(/);
    assert.doesNotMatch(body, /\(\)\s*=>\s*(ok|api)\b/);
    assert.doesNotMatch(body, /\.fn\(\)/);
});
