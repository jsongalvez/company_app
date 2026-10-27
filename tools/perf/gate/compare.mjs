// Compares the base and head backends' request logs from interleaved rounds.
//   node compare.mjs <dir> [summary.md]
// <dir> holds base-r1.log .. base-rN.log and head-r1.log .. head-rN.log. Each log has the
// request_completed JSON lines the backend wrote during one round (other lines are ignored).
// A key route fails when the head's query count rises, or when head p95 is over base p95 by more
// than SLOWER_RATIO and SLOWER_MS in at least FAIL_ROUNDS rounds.
import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

export const SLOWER_RATIO = 0.3;
export const SLOWER_MS = 25;
export const FAIL_ROUNDS = 3;
const MIN_SAMPLES = 5;

export const KEY_ROUTES = {
    "POST /auth/login": "Sign-in",
    "GET /api/branches/{branchId}/today-screen": "Today",
    "POST /api/sessions": "Save session",
    "GET /api/branches/{branchId}/daily-summary": "Finance daily summary",
    "GET /api/expenses": "Finance expenses",
};

// Signing in also reads what the app needs to open (rights, branches, the day, shift, alerts, Today's lists):
// one request instead of eight, so its query count may rise up to this budget. Other key routes may not rise.
export const QUERY_BUDGET = { "POST /auth/login": 40 };

const COMPLETED = /\{"event":"request_completed".*\}/;

export function parseLog(text) {
    const byRoute = new Map();
    for (const line of text.split("\n")) {
        const m = COMPLETED.exec(line);
        if (!m) continue;
        let e;
        try {
            e = JSON.parse(m[0]);
        } catch {
            continue;
        }
        if (e.status < 200 || e.status >= 300 || !e.template || e.template === "UNMATCHED") continue;
        const key = `${e.method} ${e.template}`;
        if (!byRoute.has(key)) byRoute.set(key, []);
        byRoute.get(key).push({ ms: e.elapsedMs, queries: e.queries });
    }
    return byRoute;
}

export const p95 = (values) => {
    const s = [...values].sort((a, b) => a - b);
    return s[Math.ceil(0.95 * s.length) - 1];
};
const median = (v) => {
    const s = [...v].sort((a, b) => a - b);
    return s[Math.floor((s.length - 1) / 2)];
};
const mode = (v) => {
    const n = new Map();
    for (const x of v) n.set(x, (n.get(x) ?? 0) + 1);
    return [...n].sort((a, b) => b[1] - a[1] || a[0] - b[0])[0][0];
};

export const isSlower = (base, head) => head > base * (1 + SLOWER_RATIO) && head - base > SLOWER_MS;

export function compare(rounds) {
    const keys = new Set();
    for (const side of ["base", "head"]) for (const r of rounds[side]) for (const k of r.keys()) keys.add(k);
    const rows = [];
    for (const key of [...keys].sort()) {
        const name = KEY_ROUTES[key];
        const pairs = rounds.head.map((h, i) => [rounds.base[i]?.get(key), h.get(key)]).filter(([b, h]) => b?.length >= MIN_SAMPLES && h?.length >= MIN_SAMPLES);
        const all = (side) => rounds[side].flatMap((r) => r.get(key) ?? []);
        const baseQ = all("base").map((x) => x.queries).filter((q) => q !== undefined);
        const headQ = all("head").map((x) => x.queries).filter((q) => q !== undefined);
        const row = { key, name: name ?? key, isKey: Boolean(name), rounds: pairs.length, slowRounds: 0, baseP95: null, headP95: null, baseQueries: null, headQueries: null, problems: [] };
        if (pairs.length) {
            const bs = pairs.map(([b]) => p95(b.map((x) => x.ms)));
            const hs = pairs.map(([, h]) => p95(h.map((x) => x.ms)));
            row.baseP95 = median(bs);
            row.headP95 = median(hs);
            row.slowRounds = bs.filter((b, i) => isSlower(b, hs[i])).length;
        }
        if (baseQ.length && headQ.length) {
            row.baseQueries = mode(baseQ);
            row.headQueries = mode(headQ);
        }
        if (row.isKey) {
            if (!pairs.length) row.problems.push("no samples on one side");
            if (row.slowRounds >= FAIL_ROUNDS) row.problems.push("slower");
            if (row.headQueries !== null && row.headQueries > Math.max(row.baseQueries, QUERY_BUDGET[key] ?? 0)) row.problems.push("more queries");
        }
        rows.push(row);
    }
    for (const key of Object.keys(KEY_ROUTES)) {
        if (!rows.some((r) => r.key === key)) rows.push({ key, name: KEY_ROUTES[key], isKey: true, rounds: 0, slowRounds: 0, baseP95: null, headP95: null, baseQueries: null, headQueries: null, problems: ["no samples on one side"] });
    }
    return { rows, failed: rows.some((r) => r.problems.length) };
}

const ms = (v) => (v === null ? "-" : `${v}ms`);
export function render({ rows, failed }, roundCount) {
    const verdict = (r) => (!r.isKey ? "not gated" : r.problems.length ? "FAIL" : "ok");
    const sorted = [...rows].sort((a, b) => Number(b.isKey) - Number(a.isKey) || a.name.localeCompare(b.name));
    const out = [`## Perf gate: ${failed ? "FAIL" : "pass"} (${roundCount} rounds)`, ""];
    for (const r of sorted.filter((x) => x.problems.length)) {
        out.push(`- ${r.name} p95 base ${ms(r.baseP95)} head ${ms(r.headP95)}, queries ${r.baseQueries ?? "-"} to ${r.headQueries ?? "-"}, ${verdict(r)} (${r.problems.join(", ")}; slower in ${r.slowRounds} of ${r.rounds} rounds)`);
    }
    if (failed) out.push("");
    out.push("| Route | Base p95 | Head p95 | Slower rounds | Queries base to head | Verdict |", "| --- | --- | --- | --- | --- | --- |");
    for (const r of sorted) out.push(`| ${r.name} | ${ms(r.baseP95)} | ${ms(r.headP95)} | ${r.slowRounds} of ${r.rounds} | ${r.baseQueries ?? "-"} to ${r.headQueries ?? "-"} | ${verdict(r)} |`);
    out.push("", `Fails when head p95 is over base by ${SLOWER_RATIO * 100}% and ${SLOWER_MS}ms in ${FAIL_ROUNDS} of the rounds, or when the query count rises (sign-in may rise up to ${QUERY_BUDGET["POST /auth/login"]}). p95 columns are medians over rounds of each round's p95.`);
    return out.join("\n") + "\n";
}

export function loadRounds(dir) {
    const files = readdirSync(dir);
    const n = Math.max(0, ...files.map((f) => Number(/^(?:base|head)-r(\d+)\.log$/.exec(f)?.[1] ?? 0)));
    const read = (side, i) => {
        const f = join(dir, `${side}-r${i}.log`);
        return files.includes(`${side}-r${i}.log`) ? parseLog(readFileSync(f, "utf8")) : new Map();
    };
    const idx = Array.from({ length: n }, (_, i) => i + 1);
    return { base: idx.map((i) => read("base", i)), head: idx.map((i) => read("head", i)) };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
    const [dir, summary] = process.argv.slice(2);
    if (!dir) {
        console.error("usage: compare.mjs <dir> [summary.md]");
        process.exit(2);
    }
    const rounds = loadRounds(dir);
    const result = compare(rounds);
    const text = render(result, rounds.head.length);
    process.stdout.write(text);
    if (summary) writeFileSync(summary, text);
    process.exit(result.failed ? 1 : 0);
}
