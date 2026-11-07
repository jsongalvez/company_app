// Static check on web styles: colours and sizes come from web/src/ui/tokens.css, never as raw values.
// Older files keep a count of raw values in design-lint.baseline.json that may only go down.
// WRITE_DESIGN_BASELINE=1 tests/unit/run.sh rewrites the counts after a file sheds raw values.
import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join, relative } from "node:path";

const SRC = join(process.cwd(), "src");
const TOKENS = "ui/tokens.css";
const BASELINE = join(process.cwd(), "tests/unit/design-lint.baseline.json");

const COLOUR = /#[0-9a-fA-F]{3,8}\b|\b(?:rgb|rgba|hsl|hsla|oklch|lab|lch)\(/;
const SIZED =
    /^(?:font|font-size|padding(?:-\w+)*|margin(?:-\w+)*|gap|row-gap|column-gap|inset(?:-\w+)*|top|right|bottom|left|(?:min-|max-)?(?:width|height)|flex|flex-basis|grid-template-(?:columns|rows)|grid-auto-(?:columns|rows))$/;
const LENGTH = /(?<![\w.])(-?\d*\.?\d+)(px|rem|em|pt)\b/g;

export type Finding = { file: string; line: number; text: string };

function files(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
        const p = join(dir, e.name);
        return e.isDirectory() ? files(p) : /\.(svelte|css)$/.test(p) ? [p] : [];
    });
}

/** The CSS a file carries: every <style> block and style="..." attribute of a .svelte file, or the whole .css file. */
function styleRuns(path: string, src: string): { at: number; css: string }[] {
    if (path.endsWith(".css")) return [{ at: 0, css: src }];
    const runs = [...src.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map((m) => ({ at: m.index! + m[0].indexOf(">") + 1, css: m[1] }));
    for (const m of src.matchAll(/\bstyle="([^"]*)"/g)) runs.push({ at: m.index! + 7, css: m[1] });
    return runs;
}

/** One finding per declaration that writes a raw colour, or a raw length in a size the token scale covers (0 and 1px are free). */
export function rawValues(file: string, src: string): Finding[] {
    const found: Finding[] = [];
    for (const run of styleRuns(file, src)) {
        const css = run.css
            .replace(/\/\*[\s\S]*?\*\//g, (c) => " ".repeat(c.length))
            .replace(/@(?:media|container|supports)[^{]*/g, (c) => " ".repeat(c.length));
        for (const d of css.matchAll(/(--)?([a-z][a-z0-9-]*)\s*:\s*([^;{}]+)/g)) {
            const [whole, custom, prop, value] = d;
            const raw =
                COLOUR.test(value) ||
                (!custom && SIZED.test(prop) && [...value.matchAll(LENGTH)].some((l) => !(Number(l[1]) === 0 || (l[1] === "1" && l[2] === "px"))));
            if (raw) found.push({ file, line: src.slice(0, run.at + d.index!).split("\n").length, text: whole.trim().replace(/\s+/g, " ") });
        }
    }
    return found;
}

function scan(): Map<string, Finding[]> {
    const byFile = new Map<string, Finding[]>();
    for (const p of files(SRC)) {
        const file = relative(SRC, p);
        if (file === TOKENS) continue;
        const f = rawValues(file, readFileSync(p, "utf8"));
        if (f.length) byFile.set(file, f);
    }
    return byFile;
}

test("rawValues finds raw colours and lengths, and lets tokens, 0 and 1px through", () => {
    const svelte = `<div style="width: 64px"></div>\n<style>\n  .a { color: #fff; padding: var(--s2); border: 1px solid var(--line); }\n  .b { margin: 0; font-size: 13px; }\n  @media (max-width: 640px) { .c { gap: var(--s1); } }\n  .d { --x: 4px; background: rgb(0 0 0); }\n</style>`;
    const got = rawValues("x.svelte", svelte).map((f) => `${f.line} ${f.text}`);
    assert.deepEqual(got, ["3 color: #fff", "4 font-size: 13px", "6 background: rgb(0 0 0)", "1 width: 64px"]);
});

test("no file writes more raw colours or sizes than its baseline, and the baseline only shrinks", () => {
    const found = scan();
    const counts = Object.fromEntries([...found].sort(([a], [b]) => a.localeCompare(b)).map(([f, v]) => [f, v.length]));
    if (process.env.WRITE_DESIGN_BASELINE === "1") {
        writeFileSync(BASELINE, JSON.stringify(counts, null, 2) + "\n");
        return;
    }
    const baseline: Record<string, number> = existsSync(BASELINE) ? JSON.parse(readFileSync(BASELINE, "utf8")) : {};
    const errors: string[] = [];
    for (const [file, list] of found) {
        const allowed = baseline[file] ?? 0;
        if (list.length > allowed)
            errors.push(
                `${file}: ${list.length} raw colours or sizes, ${allowed} allowed. Use a token from web/src/${TOKENS} instead:\n` +
                    list.map((f) => `    ${f.file}:${f.line}  ${f.text}`).join("\n"),
            );
    }
    for (const [file, allowed] of Object.entries(baseline)) {
        const now = found.get(file)?.length ?? 0;
        if (now < allowed) errors.push(`${file}: ${now} raw values left, baseline says ${allowed}. Lower it: WRITE_DESIGN_BASELINE=1 tests/unit/run.sh`);
    }
    assert.equal(errors.length, 0, "\n" + errors.join("\n"));
});
