import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { join, relative } from "node:path";
import { test } from "node:test";

// Today's state lives in web/src/lib/today.svelte.ts. svelte-check stops writes through `today.*` (it is Readonly);
// this stops the state growing back elsewhere and the lifecycle hooks being called from screens.
const src = join(process.cwd(), "src");
const OWNER = "lib/today.svelte.ts";
const files = (dir: string): string[] =>
    readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
        e.isDirectory() ? files(join(dir, e.name)) : /\.(ts|svelte)$/.test(e.name) ? [join(dir, e.name)] : [],
    );
const all = files(src).map((f) => ({ path: relative(src, f), text: readFileSync(f, "utf8") }));

const hits = (re: RegExp, allowed: string[]) =>
    all
        .filter((f) => !allowed.includes(f.path) && !f.path.endsWith("schema.d.ts"))
        .flatMap((f) => (f.text.match(re) ? [`${f.path}: ${f.text.match(re)![0]}`] : []));

test("Today's state is not kept on app", () => {
    assert.deepEqual(hits(/\bapp\.(today|shift|shiftKnown|marking)\b/, []), [], `read it from \`today\` in ${OWNER}`);
});

test("only today.svelte.ts hands Today's lists to the loaders", () => {
    assert.deepEqual(hits(/\bseedTodayScreen\s*\(/, [OWNER, "lib/data.ts"]), [], `call takeLists from ${OWNER}`);
});

test("only app.svelte.ts drives Today's lifecycle", () => {
    assert.deepEqual(hits(/\b(takeDay|restoreDay|resetDay|catchUpDay)\b/, [OWNER, "lib/app.svelte.ts"]), [], "screens call the actions, not the lifecycle");
});

test("Today's actions live in today.svelte.ts only", () => {
    const actions = /export (async )?function (clockIn|clockOut|markMember|selectBranch|refreshDay|refreshShift|onDutyAt|showMark|applyStart|applyHeldClock)\b/;
    assert.deepEqual(hits(actions, [OWNER]), [], `move it into ${OWNER}`);
});
