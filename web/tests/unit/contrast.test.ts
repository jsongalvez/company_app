// Every colour pair the screens use for text reads at WCAG AA in both themes: 4.5:1 for text, 3:1 for the focus ring.
// The colours come from web/src/ui/tokens.css, so a token edit that loses contrast fails here.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const css = readFileSync(join(process.cwd(), "src/ui/tokens.css"), "utf8");

function block(selector: string): Record<string, string> {
    const at = css.indexOf(selector);
    assert.ok(at >= 0, `tokens.css has no ${selector} block`);
    const body = css.slice(css.indexOf("{", at) + 1, css.indexOf("}", at));
    return Object.fromEntries([...body.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)].map((m) => [m[1], m[2]]));
}

function luminance(hex: string): number {
    const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrast(a: string, b: string): number {
    const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
    return (hi + 0.05) / (lo + 0.05);
}

const TEXT_ON: Record<string, string[]> = {
    text: ["bg", "panel", "panel-2", "warn-soft", "ok-soft", "bad-soft", "accent-soft", "row-sel", "row-hover"],
    "text-2": ["bg", "panel", "panel-2"],
    "text-3": ["bg", "panel", "panel-2"],
    "accent-text": ["accent"],
    ok: ["panel", "ok-soft"],
    warn: ["panel", "warn-soft"],
    bad: ["panel", "bad-soft"],
    "badge-text": ["badge"],
};
const RING_ON = ["bg", "panel"];

test("the contrast formula gives the WCAG reference values", () => {
    assert.equal(contrast("#000000", "#ffffff").toFixed(2), "21.00");
    assert.equal(contrast("#777777", "#ffffff").toFixed(2), "4.48");
    assert.equal(contrast("#ffffff", "#ffffff").toFixed(2), "1.00");
});

for (const [theme, selector] of [
    ["light", '[data-theme="light"]'],
    ["dark", '[data-theme="dark"]'],
] as const) {
    test(`text reads at 4.5:1 and the focus ring at 3:1 in the ${theme} theme`, () => {
        const t = block(selector);
        const low: string[] = [];
        for (const [fg, bgs] of Object.entries(TEXT_ON))
            for (const bg of bgs) {
                assert.ok(t[fg] && t[bg], `tokens.css ${theme} lacks --${fg} or --${bg}`);
                const r = contrast(t[fg], t[bg]);
                if (r < 4.5) low.push(`--${fg} on --${bg}: ${r.toFixed(2)}:1, needs 4.5:1`);
            }
        for (const bg of RING_ON) {
            const r = contrast(t.focus, t[bg]);
            if (r < 3) low.push(`--focus on --${bg}: ${r.toFixed(2)}:1, needs 3:1`);
        }
        assert.deepEqual(low, []);
    });
}
