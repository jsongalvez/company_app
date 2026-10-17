import { expect } from "e2e";

type Screen = Parameters<Parameters<typeof import("@e2e-dev/web").test>[1]>[0]["screen"];
type App = Parameters<Parameters<typeof import("@e2e-dev/web").test>[1]>[0]["app"];

/** Clock in at the current branch unless already on duty. Waits for the top bar to know the shift first. */
export async function clockInHere(screen: Screen): Promise<void> {
    await expect(screen.getByRole("button", /Clock (in|out)/).first()).toBeVisible();
    const clockIn = screen.getByRole("button", "Clock in");
    if ((await clockIn.count()) > 0) await clockIn.first().tap();
    await expect(screen.getByRole("button", /Clock out/).first()).toBeVisible();
}

/** Clock in at the current branch, add a session for a brand-new client, mark it done. Returns the client's name. */
export async function doneSessionForNewClient(app: App, screen: Screen): Promise<string> {
    const first = `Test${Date.now().toString().slice(-6)}`;
    await app.open("/#/today");
    await clockInHere(screen);

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
    await dialog.getByRole("button", "Add session").tap();

    const row = screen.getByRole("row").filter({ hasText: `${first} Tester` });
    await expect(row).toBeVisible();
    await row.getByRole("button", "Done").tap();
    await expect(row.getByText("Done")).toBeVisible();
    return `${first} Tester`;
}
