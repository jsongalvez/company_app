import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { doneSessionForNewClient } from "./support";

// Patterns row: view state was lost on reload or navigation (reports d75eeba4 d70d691f ee926846 746a0c70 f7689735).
// Each list page keeps its filter in the address: pick a non-default one, reload, and the same page shows it again.
const lists = [
    { path: "/clients", set: "Name or phone number", kind: "text" },
    { path: "/inventory", set: "Filter products", kind: "text" },
    { path: "/catalog", set: "Filter by name", kind: "text" },
    { path: "/users", set: "Filter by name", kind: "text" },
    { path: "/finance", set: "Show deleted", kind: "check" },
] as const;

for (const l of lists) {
    test(`${l.path} keeps its filter after a reload`, { session: "owner" }, async ({ app, screen, browser }) => {
        await app.open(`/#${l.path}`);
        const control = l.kind === "text" ? screen.getByPlaceholder(l.set) : screen.getByLabel(l.set);
        if (l.kind === "text") await control.fill("zq");
        else await control.check();
        await expect.poll(async () => new URL(await browser.url()).hash).toContain("?");
        const before = new URL(await browser.url()).hash;
        await browser.evaluate(() => location.reload());
        if (l.kind === "text") await expect(control).toHaveValue("zq");
        else await expect(control).toBeChecked();
        expect(new URL(await browser.url()).hash).toBe(before);
    });
}

// Back on a session went to Today after a reload, whatever page the session was opened from.
test("Back on a session returns to the page it came from, even after a reload", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    await app.open("/#/finance");
    await screen.getByRole("row").filter({ hasText: name }).tap();
    await expect.poll(async () => new URL(await browser.url()).hash).toMatch(/^#\/sessions\//);
    await browser.evaluate(() => location.reload());
    await screen.getByRole("button", "‹ Back").tap();
    await expect.poll(async () => new URL(await browser.url()).hash).toBe("#/finance");
});

// Alerts closes back to the page it was opened from, after a reload too.
test("closing Alerts after a reload goes back to the page it was opened from", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/clients");
    await screen.getByRole("banner").getByRole("link", "Alerts").tap();
    await expect(screen.getByRole("heading", "Alerts")).toBeVisible();
    await browser.evaluate(() => location.reload());
    await screen.getByRole("banner").getByRole("link", "Close alerts").tap();
    await expect(screen.getByRole("heading", "Clients")).toBeVisible();
});
