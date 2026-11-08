// The server writes rights.vectors.json from Access.allows (backend WebRightsVectorsTest): each person's rights as
// /api/start sends them, and what the server answers for every act at six places. The web's lookup must agree.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { allows, reads, type Act, type Place, type Rights } from "../../src/lib/rights";

type Answer = { may: Act[]; reads: Act[] };
const file = JSON.parse(readFileSync(join(process.cwd(), "tests/unit/rights.vectors.json"), "utf8")) as {
    acts: Act[];
    places: Record<string, Place>;
    vectors: { who: string; rights: Rights; answers: Record<string, Answer> }[];
};

test("the vectors cover every act at every place", () => {
    assert.ok(file.acts.length > 0 && file.vectors.length > 0);
    for (const v of file.vectors) assert.deepEqual(Object.keys(v.answers), Object.keys(file.places), v.who);
});

test("the web looks up what the server answers, for every person, act and place", () => {
    const wrong: string[] = [];
    for (const { who, rights, answers } of file.vectors) {
        for (const [name, place] of Object.entries(file.places)) {
            const got: Answer = {
                may: file.acts.filter((a) => allows(rights, a, place)),
                reads: file.acts.filter((a) => reads(rights, a, place)),
            };
            if (JSON.stringify(got) !== JSON.stringify(answers[name])) wrong.push(`${who} at ${name}: got ${JSON.stringify(got)}`);
        }
    }
    assert.deepEqual(wrong, []);
});

test("screens ask permissions only through can(act) and canRead(act), naming a server Act", () => {
    const files: string[] = [];
    const walk = (d: string) => {
        for (const n of readdirSync(d)) {
            const p = join(d, n);
            if (statSync(p).isDirectory()) walk(p);
            else if (/\.(svelte|ts)$/.test(n)) files.push(p);
        }
    };
    walk(join(process.cwd(), "src"));
    const helper = /\bcan(?!Read\b|Queue\b)[A-Z]\w*\s*\(/;
    const named = /\bcan(?:Read)?\(\s*"([A-Z_]+)"/g;
    const bad: string[] = [];
    for (const f of files) {
        const src = readFileSync(f, "utf8");
        src.split("\n").forEach((line, i) => {
            if (helper.test(line)) bad.push(`${f}:${i + 1} ${line.trim()}`);
        });
        for (const m of src.matchAll(named)) if (!file.acts.includes(m[1] as Act)) bad.push(`${f}: can("${m[1]}") names no server Act`);
    }
    assert.deepEqual(bad, []);
});
