import { test } from "@e2e-dev/web";
import { expect, credentials } from "e2e";

test.setup("sign in as owner", { sessions: ["owner"] }, async ({ app, screen, session }) => {
    const owner = credentials.user("owner");
    await app.open("/");
    await screen.getByLabel("Username").fill(owner.username);
    await screen.getByLabel("Password").fill(owner.password);
    await screen.getByRole("button", "Sign in").tap();
    await expect(screen.getByRole("navigation", "Main")).toBeVisible();
    await session.save("owner");
});
