// A formatter (Intl.DateTimeFormat, Intl.NumberFormat, toLocale*String with options) is built once at the
// top of web/src/lib/format.ts, never per call: building one costs about 1ms on a slow phone, and the menu
// tap once spent 89ms of its 440ms building operationalDate's (tools/perf/tap-profile.mjs, 4x CPU).
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";
import { manilaTime, monthTitle, operationalDate, wallTime } from "../../src/lib/format";

const SRC = join(process.cwd(), "src");

function sources(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
        const p = join(dir, e.name);
        return e.isDirectory() ? sources(p) : /\.(ts|svelte)$/.test(p) && !p.endsWith(".gen.ts") ? [p] : [];
    });
}

const BUILD = /new Intl\.|\.toLocale(Date|Time)?String\([^)]/;

test("formatters are built once, at the top of lib/format.ts", () => {
    const bad: string[] = [];
    for (const f of sources(SRC)) {
        const rel = relative(SRC, f);
        readFileSync(f, "utf8")
            .split("\n")
            .forEach((line, i) => {
                if (!BUILD.test(line)) return;
                if (rel === "lib/format.ts" && /^const \w+ = new Intl\./.test(line)) return;
                bad.push(`${rel}:${i + 1}: ${line.trim()}`);
            });
    }
    assert.deepEqual(bad, [], "Move each formatter to a top-level `const x = new Intl...` in src/lib/format.ts and export a function that uses it");
});

test("the hoisted formatters give the same text", () => {
    assert.equal(operationalDate(new Date("2026-11-04T19:59:00Z")), "2026-11-04");
    assert.equal(operationalDate(new Date("2026-11-04T20:00:00Z")), "2026-11-05");
    assert.equal(manilaTime(new Date("2026-10-18T06:30:05Z")), "2026-10-18T14:30:05");
    assert.equal(wallTime("2026-10-18T14:30:05"), "Oct 18, 2026, 2:30:05 PM");
    assert.equal(wallTime("2026-10-18T14:30"), "Oct 18, 2026, 2:30 PM");
    assert.equal(monthTitle(2026, 10), "October 2026");
});
