// Comments in web/src give a reason the code can't show, never history. Same patterns as backend detekt ForbiddenComment.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";

const SRC = join(process.cwd(), "src");
const GENERATED = new Set(["api/schema.d.ts", "lib/access.gen.ts"]);
const RULES: [RegExp, string][] = [
    [/\b[0-9a-f]{8}\b/, "history lives in git: drop the commit or feedback id (tools/quality/kill-comments.py web/src)"],
    [/\bprecedent\b/i, 'name the code it copies, or let the code show it; "precedent" stories rot'],
];
const COMMENT = /\/\/[^\n]*|\/\*[\s\S]*?\*\/|<!--[\s\S]*?-->/g;

function sources(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
        const p = join(dir, e.name);
        return e.isDirectory() ? sources(p) : /\.(ts|svelte)$/.test(p) ? [p] : [];
    });
}

test("comments carry no commit ids or precedent stories", () => {
    const hits: string[] = [];
    for (const file of sources(SRC)) {
        const rel = relative(SRC, file);
        if (GENERATED.has(rel)) continue;
        const text = readFileSync(file, "utf8");
        for (const m of text.matchAll(COMMENT)) {
            if (m[0].startsWith("//") && /:\s*$/.test(text.slice(Math.max(0, m.index! - 6), m.index!))) continue;
            for (const [rule, fix] of RULES) {
                if (rule.test(m[0])) {
                    const line = text.slice(0, m.index).split("\n").length;
                    hits.push(`${rel}:${line}: ${fix}: ${m[0].slice(0, 80)}`);
                }
            }
        }
    }
    assert.deepEqual(hits, []);
});
