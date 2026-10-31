import { test } from "@e2e-dev/web";
import { expect } from "e2e";

test("a wrong password says so, not that you were signed out", async ({ app, screen }) => {
    await app.clearState();
    await app.open("/");
    await screen.getByLabel("Username").fill(`nobody${Date.now().toString().slice(-6)}`);
    await screen.getByLabel("Password").fill("not-the-password");
    await screen.getByRole("button", "Sign in").tap();
    await expect(screen.getByText("Wrong username or password.")).toBeVisible();
    await expect(screen.getByText(/signed out/)).toHaveCount(0);
});
