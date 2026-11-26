import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { doneSessionForNewClient } from "./support";

const down = /\/(api\/|health)/;

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
