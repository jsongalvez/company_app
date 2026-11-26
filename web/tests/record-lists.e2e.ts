import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Patterns row: a date picker was the wrong control for finding old records (reports 13ddb174 266e2d40
// 2a4162b6 d4706fb2 537495d8). A record list opens on its newest records with no date picked, and its
// oldest records are reached by paging back. tools/seed/old-records.sql seeds more than a page of each.
const lists = [
    { path: "/feedback", newest: "Older report 001", oldest: "Older report 150", more: "Show older" },
    { path: "/notifications", newest: "Older alert 001", oldest: "Older alert 060", more: "Show older" },
    { path: "/audit", newest: "Older change 001", oldest: "Older change 120", more: "Show more" },
] as const;

for (const l of lists) {
    test(`${l.path} opens on the newest records and pages back to the oldest`, { session: "owner" }, async ({ app, screen }) => {
        await app.open(`/#${l.path}`);
        const rows = screen.getByRole("row");
        const row = (text: string) => rows.filter({ hasText: text }).first();
        const more = screen.getByRole("button", l.more);
        // Pages back until the record shows, waiting for each page to add rows.
        async function pageTo(text: string) {
            await expect(rows.first()).toBeVisible();
            for (let i = 0; i < 30 && !(await row(text).isVisible()); i++) {
                const shown = await rows.count();
                await more.tap();
                await expect.poll(() => rows.count()).toBeGreaterThan(shown);
            }
            await expect(row(text)).toBeVisible();
        }
        // Records other tests made are newer, so the newest seeded one may sit a page or two back.
        await pageTo(l.newest);
        await expect(row(l.oldest)).toBeHidden();
        await pageTo(l.oldest);
    });
}
