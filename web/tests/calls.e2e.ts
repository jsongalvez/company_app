import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { addDoneSessionForNewClient, clockInHere } from "./support";

// Saving or marking a session cannot change the day's status, so Today does not ask for it again.
// Only the Refresh button does.
test("saving and marking a session do not re-read the branch day", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockInHere(screen, browser);

    const dayReads = /\/api\/branches\/[^/]+\/today$/;
    let reads = 0;
    await browser.route(dayReads, async (route) => {
        if (route.request.method === "GET") reads++;
        await route.fallback();
    });

    await addDoneSessionForNewClient(screen);
    expect(reads).toBe(0);

    await screen.getByRole("button", "Refresh").tap();
    await expect.poll(() => reads).toBe(1);
});
