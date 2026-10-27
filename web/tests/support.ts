import { expect } from "e2e";

type Screen = Parameters<Parameters<typeof import("@e2e-dev/web").test>[1]>[0]["screen"];
type App = Parameters<Parameters<typeof import("@e2e-dev/web").test>[1]>[0]["app"];
type Browser = Parameters<Parameters<typeof import("@e2e-dev/web").test>[1]>[0]["browser"];

/** Clock in at the current branch unless already on duty (accepting the "are you sure"). Waits for the top bar to know the shift first. */
export async function clockInHere(screen: Screen, browser: Browser): Promise<void> {
    const bar = screen.getByRole("banner");
    await expect(bar.getByRole("button", /Clock (in|out)/).first()).toBeVisible();
    const clockIn = bar.getByRole("button", "Clock in");
    if ((await clockIn.count()) > 0) {
        const off = await browser.onDialog("accept");
        await clockIn.first().tap();
        await expect(bar.getByRole("button", /Clock out/).first()).toBeVisible();
        await off();
    }
    await expect(bar.getByRole("button", /Clock out/).first()).toBeVisible();
}

/** Clock out of the current branch if on duty (accepting the "are you sure"). */
export async function clockOutHere(screen: Screen, browser: Browser): Promise<void> {
    const bar = screen.getByRole("banner");
    await expect(bar.getByRole("button", /Clock (in|out)/).first()).toBeVisible();
    const clockOut = bar.getByRole("button", /Clock out/);
    if ((await clockOut.count()) > 0) {
        const off = await browser.onDialog("accept");
        await clockOut.first().tap();
        await expect(bar.getByRole("button", "Clock in").first()).toBeVisible();
        await off();
    }
}

/** Clock in at the current branch, add a session for a brand-new client, mark it done. Returns the client's name. */
export async function doneSessionForNewClient(app: App, screen: Screen, browser: Browser): Promise<string> {
    await app.open("/#/today");
    await clockInHere(screen, browser);
    return addDoneSessionForNewClient(screen);
}

/** On Today, already clocked in: add a session for a brand-new client and mark it done. Returns the client's name. */
export async function addDoneSessionForNewClient(screen: Screen): Promise<string> {
    const first = `Test${Date.now().toString().slice(-6)}`;
    await screen.getByRole("button", "New session").tap();
    const dialog = screen.getByRole("dialog", "New session");
    await dialog.getByPlaceholder("Search by name or phone (2+ letters)").fill(first);
    await dialog.getByRole("button", "New client").tap();
    const form = screen.getByRole("dialog", "New client");
    await form.getByLabel(/^Last name/).fill("Tester");
    await form.getByLabel(/^First name/).fill(first);
    await form.getByLabel(/^Gender/).selectOption({ value: "F" });
    await form.getByLabel(/^Age/).fill("40");
    await form.getByRole("button", "Create client").tap();
    await expect(dialog.getByText(`${first} Tester`, { exact: false })).toBeVisible();
    // The preview fills the price; wait for it so a slow response cannot submit an empty one.
    await expect(dialog.getByLabel(/^Price/)).not.toHaveValue("");
    await dialog.getByRole("button", "Add session").tap();

    const row = screen.getByRole("row").filter({ hasText: `${first} Tester` });
    await expect(row).toBeVisible();
    await row.getByRole("button", "Done").tap();
    await expect(row.getByText("Done")).toBeVisible();
    return `${first} Tester`;
}

/**
 * Count the reads the app makes, by what each one asks for. Reads asked for together travel in one
 * `/api/batch` request, so its paths are counted one by one, as if they had gone alone.
 */
export async function watchReads(browser: Browser, lists: Record<string, RegExp>): Promise<Record<string, number>> {
    const reads = Object.fromEntries(Object.keys(lists).map((name) => [name, 0]));
    const count = (path: string) => {
        for (const [name, re] of Object.entries(lists)) if (re.test(path)) reads[name]!++;
    };
    await browser.route(/\/api\//, async (route) => {
        const { url, method, postData } = route.request;
        const u = new URL(url);
        if (method === "GET") count(u.pathname + u.search);
        else if (method === "POST" && u.pathname === "/api/batch") for (const p of (JSON.parse(postData ?? "{}") as { paths?: string[] }).paths ?? []) count(p);
        await route.fallback();
    });
    return reads;
}
