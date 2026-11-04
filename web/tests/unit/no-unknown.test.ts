// The server sends only real enum values, so no screen may branch on or label a fake "UNKNOWN" one.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";

const SRC = join(process.cwd(), "src");

function sources(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
        const p = join(dir, e.name);
        return e.isDirectory() ? sources(p) : /\.(ts|svelte)$/.test(p) ? [p] : [];
    });
}

test("web/src and the generated API types name no UNKNOWN enum value", () => {
    const hits = sources(SRC)
        .filter((f) => /["']UNKNOWN["']|\bUNKNOWN:/.test(readFileSync(f, "utf8")))
        .map((f) => relative(SRC, f));
    assert.deepEqual(hits, [], "drop the UNKNOWN value; an absent value is a missing or nullable field");
});
