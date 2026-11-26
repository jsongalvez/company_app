import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { BASE, owner } from "./offline";
import { addSessionForNewClient, clockInHere, diskValues, doneSessionForNewClient } from "./support";

const down = /\/(api\/|health)/;
type Item = { path: string; failed?: string };
const saved = async (browser: Parameters<typeof diskValues>[0]) => JSON.parse((await diskValues(browser, "outbox"))[0] ?? "[]") as Item[];
type Screen = Parameters<Parameters<typeof test>[2]>[0]["screen"];

/** With no signal: add another practitioner, then change the price. Returns the price typed. */
async function addPersonThenPrice(screen: Screen) {
    const pick = screen.getByRole("combobox").filter({ hasText: "Add practitioner" });
    await pick.selectOption({ index: 1 });
    await screen.getByRole("button", "Add").tap();
    await expect(screen.getByText("1 change waiting to send", { exact: false })).toBeVisible({ timeout: 20_000 });
    await screen.getByRole("button", "Change").first().tap();
    const dialog = screen.getByRole("dialog", "Change price");
    await dialog.getByLabel(/^Final price/).fill("1234");
    await dialog.getByRole("button", "Save").tap();
    await expect(screen.getByText("2 changes waiting to send", { exact: false })).toBeVisible({ timeout: 20_000 });
}

// With no signal, a session's practitioner remarks and illnesses change on the page at once, and read the same once sent.
test("offline practitioner and illness changes show on the session, and still after they are sent", { session: "owner" }, async ({ app, screen, browser }) => {
    const tag = Date.now().toString().slice(-6);
    const kept = `Hip ache ${tag}`;
    const gone = `Wrist ache ${tag}`;
    const added = `Knee ache ${tag}`;
    const remarks = `Left side ${tag}`;
    const name = await doneSessionForNewClient(app, screen, browser);
    await screen.getByRole("row").filter({ hasText: name }).getByText(name).tap();
    const box = screen.getByLabel("Add illness");
    for (const label of [kept, gone]) {
        await box.fill(label);
        await box.press("Enter");
        await expect(screen.getByRole("button", `Remove ${label}`)).toBeVisible();
    }
    const person = screen.getByRole("row").filter({ has: screen.getByRole("button", "Remarks") });
    await expect(person).toBeVisible();

    await browser.route(down, (route) => route.abort());
    const answer = await browser.onDialog((d) => d.accept(remarks));
    await person.getByRole("button", "Remarks").tap();
    await expect(person.getByText(remarks)).toBeVisible({ timeout: 20_000 });
    await answer();
    await screen.getByRole("button", `Remove ${gone}`).tap();
    await expect(screen.getByRole("button", `Remove ${gone}`)).not.toBeVisible({ timeout: 20_000 });
    await box.fill(added);
    await box.press("Enter");
    await expect(screen.getByRole("button", `Remove ${added}`)).toBeVisible({ timeout: 20_000 });
    await expect(screen.getByText("3 changes waiting to send", { exact: false })).toBeVisible();

    await browser.unroute(down);
    await expect(screen.getByText("waiting to send", { exact: false })).not.toBeVisible({ timeout: 30_000 });
    const shown = async () => {
        await expect(person.getByText(remarks)).toBeVisible({ timeout: 15_000 });
        await expect(screen.getByRole("button", `Remove ${kept}`)).toBeVisible();
        await expect(screen.getByRole("button", `Remove ${added}`)).toBeVisible();
        await expect(screen.getByRole("button", `Remove ${gone}`)).not.toBeVisible();
    };
    await shown();
    await browser.reload();
    await shown();
});

// The session moves one version on for each of them: the price, made on the page before the practitioner
// landed, used to go out at the old version and come back "Someone changed this".
test("offline a practitioner added and then the price changed both land", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    await screen.getByRole("row").filter({ hasText: name }).getByText(name).tap();
    const people = screen.getByRole("row").filter({ has: screen.getByRole("button", "Remarks") });
    await expect(people).toHaveCount(1);

    await browser.route(down, (route) => route.abort());
    await addPersonThenPrice(screen);
    await browser.unroute(down);

    await expect.poll(async () => (await saved(browser)).length, { timeout: 40_000 }).toBe(0);
    await expect(screen.getByText("couldn't be sent", { exact: false })).not.toBeVisible();
    await browser.reload();
    await expect(screen.getByText("1,234.00", { exact: false })).toBeVisible({ timeout: 15_000 });
    await expect(people).toHaveCount(2);
});

// Someone else changes the session while this phone is offline: the practitioner made from the old
// version is refused, and the price made on top of it waits rather than land over their change.
test("a change someone else made meanwhile is refused, never written over", { session: "owner" }, async ({ app, screen, browser }) => {
    const name = await doneSessionForNewClient(app, screen, browser);
    await screen.getByRole("row").filter({ hasText: name }).getByText(name).tap();
    await expect(screen.getByRole("button", "Remarks")).toBeVisible();
    const id = (await browser.evaluate(() => location.hash)).split("/")[2]!.split("?")[0]!;

    await browser.route(down, (route) => route.abort());
    await addPersonThenPrice(screen);
    const auth = { Authorization: `Bearer ${await owner()}`, "Content-Type": "application/json" };
    const { version } = (await (await fetch(`${BASE}/api/sessions/${id}`, { headers: auth })).json()) as { version: number };
    const theirs = await fetch(`${BASE}/api/sessions/${id}/final-price`, {
        method: "PATCH",
        headers: auth,
        body: JSON.stringify({ finalPrice: "999", version }),
    });
    expect(theirs.status).toBe(200);
    await browser.unroute(down);

    await expect.poll(async () => (await saved(browser)).filter((i) => i.failed).length, { timeout: 40_000 }).toBe(1);
    expect((await saved(browser)).map((i) => [i.path.split("/").pop(), !!i.failed])).toEqual([
        ["practitioners", true],
        ["final-price", false],
    ]);
    const after = (await (await fetch(`${BASE}/api/sessions/${id}`, { headers: auth })).json()) as { version: number; finalPrice: string };
    expect(after).toMatchObject({ version: version + 1, finalPrice: "999.00" });
});

// A mark made on Today with no signal (or still on its way) showed only on Today: the session's own page and the
// client's page still said Waiting until it landed. The price changed after it goes out from the server's version,
// since the mark moves none on this phone, so both land.
test(
    "a session marked done offline shows done on its page and the client's page, and still once sent",
    { session: "owner" },
    async ({ app, screen, browser }) => {
        await app.open("/#/today");
        await clockInHere(screen, browser);
        const name = await addSessionForNewClient(screen);
        const row = screen.getByRole("row").filter({ hasText: name });
        const sessionMark = () => browser.evaluate(() => document.querySelector("section.panel header .mark")?.textContent?.trim() ?? "");
        const clientRow = () =>
            browser.evaluate(() => {
                const r = document.querySelector("table.grid tbody tr");
                return r ? `${r.querySelector(".mark")?.textContent?.trim()} ${r.querySelector("td.num")?.textContent?.trim()}` : "";
            });
        const go = (hash: string) => browser.evaluate((h) => void (location.hash = h), hash);

        // Both pages once with signal, so the phone has them (a row opens once its session has landed).
        await expect(row.getByRole("button", "Done")).toBeVisible();
        await row.getByText(name).tap();
        await expect.poll(sessionMark).toBe("Waiting");
        const session = await browser.evaluate(() => location.hash);
        await screen.getByRole("link", name).tap();
        await expect.poll(clientRow).toMatch(/^Waiting /);
        const client = await browser.evaluate(() => location.hash);
        await go("#/today");

        await browser.route(down, (route) => route.abort());
        await row.getByRole("button", "Done").tap();
        await expect(row.getByText("Done")).toBeVisible();
        await go(session);
        await expect.poll(sessionMark, { timeout: 5_000 }).toBe("Done");
        await screen.getByRole("button", "Change").first().tap();
        const dialog = screen.getByRole("dialog", "Change price");
        await dialog.getByLabel(/^Final price/).fill("1234");
        await dialog.getByRole("button", "Save").tap();
        await expect(screen.getByText("2 changes waiting to send", { exact: false })).toBeVisible({ timeout: 20_000 });
        await go(client);
        await expect.poll(clientRow, { timeout: 5_000 }).toBe("Done 1,234.00");

        await browser.unroute(down);
        await expect.poll(async () => (await saved(browser)).length, { timeout: 40_000 }).toBe(0);
        await expect(screen.getByText("couldn't be sent", { exact: false })).not.toBeVisible();
        await expect.poll(clientRow).toBe("Done 1,234.00");
        await browser.reload();
        await expect.poll(clientRow, { timeout: 15_000 }).toBe("Done 1,234.00");
        await go(session);
        await expect.poll(sessionMark, { timeout: 15_000 }).toBe("Done");
        await expect(screen.getByText("1,234.00", { exact: false })).toBeVisible();
    },
);
