import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { join } from "node:path";
import { mock, test } from "node:test";
import type * as TS from "typescript";
import { wiring } from "../../src/lib/wiring";
import { saveSnapshotOnLeave } from "../../src/lib/snapshot";

// Background listeners and timers in web/src/lib start only from a starter, which returns how to stop them
// (lib/wiring.ts). Guards: nothing starts at import time, raw listeners stay on a short list, every starter
// returns `Stop`, and stop removes every listener and timer.
const ts = createRequire(join(process.cwd(), "package.json"))("typescript") as typeof TS;
const lib = join(process.cwd(), "src/lib");
const files = readdirSync(lib)
    .filter((f) => f.endsWith(".ts") && !f.endsWith(".d.ts"))
    .map((f) => ({ name: f, ast: ts.createSourceFile(f, readFileSync(join(lib, f), "utf8"), ts.ScriptTarget.Latest, true) }));

const callee = (n: TS.CallExpression) => {
    const e = n.expression;
    return ts.isIdentifier(e) ? e.text : ts.isPropertyAccessExpression(e) ? e.name.text : "";
};
const isFunction = (n: TS.Node) => ts.isFunctionLike(n) || ts.isClassDeclaration(n) || ts.isClassExpression(n);
function calls(node: TS.Node, names: RegExp, intoFunctions: boolean, out: TS.CallExpression[] = []) {
    node.forEachChild((c) => {
        if (!intoFunctions && isFunction(c)) return;
        if (ts.isCallExpression(c) && names.test(callee(c))) out.push(c);
        calls(c, names, intoFunctions, out);
    });
    return out;
}
const where = (f: (typeof files)[number], n: TS.Node) => `${f.name}:${f.ast.getLineAndCharacterOfPosition(n.getStart()).line + 1} ${n.getText().slice(0, 60)}`;

test("nothing in web/src/lib listens, starts a timer or rewrites history at import time", () => {
    const found = files.flatMap((f) =>
        calls(f.ast, /^(addEventListener|setInterval|setTimeout|requestAnimationFrame|replaceState|pushState|observe)$/, false).map((c) => where(f, c)),
    );
    assert.deepEqual(found, [], "move it into a starter called from main.ts (lib/wiring.ts)");
});

// Raw listeners and intervals outside wiring(), each one undoing itself.
const RAW: Record<string, number> = {
    "changed.ts": 1, // on a page element, gone with it
    "cache.svelte.ts": 1, // once, for one query waiting on the signal
    "net.svelte.ts": 2, // onlineOrTimeout removes its own; the /health ping clears itself and on watchConnection's stop
    "outbox.svelte.ts": 1, // whenLanded returns its own stop
    "wiring.ts": 2,
};

test("listeners and intervals in web/src/lib go through wiring(), so a stop handle can undo them", () => {
    const counts: Record<string, number> = {};
    for (const f of files) {
        const n = calls(f.ast, /^(addEventListener|setInterval)$/, true).length;
        if (n) counts[f.name] = n;
    }
    assert.deepEqual(counts, RAW, "use wiring().on/every in a starter; a new raw one needs a reason on this list");
});

test("every function that sets up wiring returns its Stop", () => {
    const bad: string[] = [];
    for (const f of files) {
        const visit = (n: TS.Node) => {
            if (ts.isFunctionDeclaration(n) && calls(n, /^wiring$/, false).length && n.type?.getText() !== "Stop") bad.push(where(f, n));
            n.forEachChild(visit);
        };
        visit(f.ast);
    }
    assert.deepEqual(bad, [], "declare `: Stop` and return w.stop");
});

/** An EventTarget that counts what is still attached. */
class Counted extends EventTarget {
    live = 0;
    override addEventListener(...a: Parameters<EventTarget["addEventListener"]>) {
        this.live++;
        super.addEventListener(...a);
    }
    override removeEventListener(...a: Parameters<EventTarget["removeEventListener"]>) {
        this.live--;
        super.removeEventListener(...a);
    }
}

test("stop removes every listener and timer, twice is harmless, and it starts again cleanly", () => {
    mock.timers.enable({ apis: ["setInterval", "setTimeout"] });
    try {
        const target = new Counted();
        let heard = 0;
        let ticks = 0;
        let fired = 0;
        const start = () => {
            const w = wiring();
            w.on(target, "ping", () => heard++);
            w.on(target, "ping", () => heard++, { capture: true });
            w.every(1000, () => ticks++);
            w.after(500, () => fired++);
            const inner = wiring();
            inner.on(target, "ping", () => heard++);
            w.also(inner.stop);
            return w.stop;
        };
        const stop = start();
        assert.equal(target.live, 3);
        target.dispatchEvent(new Event("ping"));
        mock.timers.tick(1000);
        assert.deepEqual([heard, ticks, fired], [3, 1, 1]);

        stop();
        stop();
        assert.equal(target.live, 0);
        target.dispatchEvent(new Event("ping"));
        mock.timers.tick(5000);
        assert.deepEqual([heard, ticks, fired], [3, 1, 1]);

        const again = start();
        assert.equal(target.live, 3);
        again();
        mock.timers.tick(5000);
        assert.deepEqual([target.live, ticks, fired], [0, 1, 1], "a timer stopped before it fires never fires");
    } finally {
        mock.timers.reset();
    }
});

test("saveSnapshotOnLeave adds its two listeners and its stop removes both", () => {
    const g = globalThis as Record<string, unknown>;
    const saved = { window: g.window, document: g.document };
    const win = new Counted();
    const doc = Object.assign(new Counted(), { visibilityState: "visible" });
    Object.assign(g, { window: win, document: doc });
    try {
        const stop = saveSnapshotOnLeave();
        assert.deepEqual([win.live, doc.live], [1, 1]);
        stop();
        stop();
        assert.deepEqual([win.live, doc.live], [0, 0]);
        saveSnapshotOnLeave()();
        assert.deepEqual([win.live, doc.live], [0, 0]);
    } finally {
        Object.assign(g, saved);
    }
});
