// Static check on the UI text in web/src/**/*.svelte. Runs with the other unit tests (tests/unit/run.sh, CI browser.yml).
// The rule: a screen says what it needs through layout, labels and live values, not through explainer sentences.
// Every error names the fix, so an agent that trips it knows what to change.
import { test } from "node:test";
import { readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";

const SRC = join(process.cwd(), "src");

// PageHead `sub=` may carry a live value the shell does not already show. Never a tagline. Keep this list short.
const SUB_ALLOWED: Record<string, string> = {
    "pages/SessionDetail.svelte": "date and type of this session",
    "pages/RemittanceDetail.svelte": "branch and date range of this remittance (can differ from the shell's branch)",
};
// A helper element (muted/faint/hint/note) may hold a label and a live value, not a sentence.
const HELPER_MAX_WORDS = 5;
const HELPER_CLASSES = ["muted", "faint", "hint", "note"];
// The one line a dialog may keep about an irreversible action: <p class="consequence">, one per dialog.
const CONSEQUENCE_MAX_WORDS = 10;

function svelteFiles(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
        const p = join(dir, e.name);
        return e.isDirectory() ? svelteFiles(p) : p.endsWith(".svelte") ? [p] : [];
    });
}

/** Words a reader sees: tags and block markers dropped, `{expr}` reduced to the string literals inside it. */
function words(inner: string): string[] {
    const text = inner
        .replace(/<[^>]*>/g, " ")
        .replace(/\{[#/:@][^}]*\}/g, " ")
        .replace(/\{([^}]*)\}/g, (_, e: string) =>
            (e.match(/"[^"]*"|'[^']*'|`[^`]*`/g) ?? []).map((s) => s.slice(1, -1).replace(/\$\{[^}]*\}/g, " ")).join(" "),
        );
    return text.split(/\s+/).filter((w) => /[A-Za-z]/.test(w));
}

const lineOf = (src: string, index: number) => src.slice(0, index).split("\n").length;

const START_TAG = /<(div|p|span|small)\b(?:[^>"'{]|"[^"]*"|'[^']*'|\{[^}]*\})*>/g;

/** Every div/p/span/small with its class list and inner markup. Nested elements are found too, not swallowed by their parent. */
function elements(src: string): { index: number; classes: string[]; inner: string }[] {
    const found: { index: number; classes: string[]; inner: string }[] = [];
    for (const m of src.matchAll(START_TAG)) {
        if (m[0].endsWith("/>")) continue;
        const classes = (m[0].match(/\bclass="([^"]*)"/)?.[1] ?? "").split(/\s+/);
        const tag = new RegExp(`<${m[1]}\\b|</${m[1]}>`, "g");
        tag.lastIndex = m.index + m[0].length;
        let depth = 1;
        for (let t = tag.exec(src); t; t = tag.exec(src)) {
            depth += t[0].startsWith("</") ? -1 : 1;
            if (depth === 0) {
                found.push({ index: m.index, classes, inner: src.slice(m.index + m[0].length, t.index) });
                break;
            }
        }
    }
    return found;
}

const files = svelteFiles(SRC).map((f) => ({ rel: relative(SRC, f), at: `web/src/${relative(SRC, f)}`, src: readFileSync(f, "utf8") }));

function fail(problems: string[]) {
    if (problems.length) throw new Error(`\n${problems.join("\n")}\n`);
}

test("PageHead sub= is only a live value, in the short allowlist", () => {
    const problems: string[] = [];
    for (const f of files) {
        for (const m of f.src.matchAll(/<PageHead\b[^>]*?\bsub=("[^"]*"|\{)/g)) {
            const staticText = m[1].startsWith('"') && !m[1].includes("{");
            const where = `${f.at}:${lineOf(f.src, m.index)}`;
            if (staticText) problems.push(`${where}: PageHead sub= is a static tagline. Remove it; if the title needs it to make sense, fix the layout.`);
            else if (!(f.rel in SUB_ALLOWED))
                problems.push(
                    `${where}: PageHead sub= repeats what the shell shows (branch, day) or what the page shows. Remove it. Allowed only in: ${Object.keys(SUB_ALLOWED).join(", ")}.`,
                );
        }
    }
    fail(problems);
});

test("helper text is a label or a live value, not a sentence", () => {
    const problems: string[] = [];
    for (const f of files) {
        for (const el of elements(f.src)) {
            if (!el.classes.some((c) => HELPER_CLASSES.includes(c)) || el.classes.includes("consequence")) continue;
            const w = words(el.inner);
            if (w.length > HELPER_MAX_WORDS)
                problems.push(
                    `${f.at}:${lineOf(f.src, el.index)}: helper text of ${w.length} words (max ${HELPER_MAX_WORDS}): "${w.join(" ").slice(0, 60)}…" ` +
                        `Put the effect on the button label or show the live value; remove the explainer. If the screen needs it to make sense, fix the layout.`,
                );
        }
    }
    fail(problems);
});

test("a dialog keeps at most one short consequence line", () => {
    const problems: string[] = [];
    for (const f of files) {
        for (const d of f.src.matchAll(/<Dialog\b[\s\S]*?<\/Dialog>/g)) {
            const lines = [...d[0].matchAll(/<(div|p)\b[^>]*?\bclass="[^"]*\bconsequence\b[^"]*"[^>]*>([\s\S]*?)<\/\1>/g)];
            const at = `${f.at}:${lineOf(f.src, d.index)}`;
            if (lines.length > 1)
                problems.push(
                    `${at}: ${lines.length} consequence lines in one dialog. Keep the one that decides the action; label the button with the effect.`,
                );
            for (const l of lines) {
                const w = words(l[2]);
                if (w.length > CONSEQUENCE_MAX_WORDS)
                    problems.push(`${at}: consequence line of ${w.length} words (max ${CONSEQUENCE_MAX_WORDS}). State only what cannot be undone.`);
            }
        }
    }
    fail(problems);
});
