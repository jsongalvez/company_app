// The 04:00 business day lives in web/src/lib/format.ts (operationalDate) and, for browser tests, in
// web/tests/clock.ts. These tests fail when other code reads the wall clock for a date by hand.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { monthBounds } from "../../src/lib/format";

const SRC_ONLY_IN_FORMAT = /new Date\(\)|toISOString\(\)\.slice|toLocaleDateString/;
const NO_CLOCK_IN_BROWSER_TESTS = /new Date\(/;

function files(dir: string, keep: (name: string) => boolean): string[] {
    return readdirSync(dir, { withFileTypes: true, recursive: true })
        .filter((e) => e.isFile() && keep(e.name))
        .map((e) => join(e.parentPath, e.name));
}

function offenders(paths: string[], banned: RegExp): string[] {
    return paths.flatMap((p) =>
        readFileSync(p, "utf8")
            .split("\n")
            .flatMap((line, i) => (banned.test(line) ? [`${p}:${i + 1}: ${line.trim()}`] : [])),
    );
}

test("web/src builds dates only in format.ts", () => {
    const src = files("src", (n) => /\.(ts|svelte)$/.test(n)).filter((p) => !p.endsWith("lib/format.ts"));
    assert.deepEqual(offenders(src, SRC_ONLY_IN_FORMAT), [], "use operationalDate, addDays, monthBounds, now or nowIso from lib/format.ts");
});

test("browser tests get dates from tests/clock.ts, never from new Date", () => {
    const tests = files("tests", (n) => /\.e2e\.ts$/.test(n) || n === "support.ts");
    assert.deepEqual(offenders(tests, NO_CLOCK_IN_BROWSER_TESTS), [], "use businessDate or manilaTime from tests/clock.ts");
});

test("monthBounds steps whole months across a year end and a leap February", () => {
    assert.deepEqual(monthBounds(2026, 10, 0), { from: "2026-10-01", to: "2026-10-31" });
    assert.deepEqual(monthBounds(2026, 1, -1), { from: "2025-12-01", to: "2025-12-31" });
    assert.deepEqual(monthBounds(2026, 12, 1), { from: "2027-01-01", to: "2027-01-31" });
    assert.deepEqual(monthBounds(2028, 1, 1), { from: "2028-02-01", to: "2028-02-29" });
});
