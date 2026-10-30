import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Signs in once per role user (tools/seed/roles.sql) and the relief worker, for roles.e2e.ts. Signing
// in again inside each test would pass the server's limit of 10 sign-ins a minute from one address.
const ROLES = ["superuser", "owner", "manager", "coordinator", "practitioner", "accountant", "relief"] as const;
const logins = [
    ...ROLES.map((r) => ({ session: `role-${r}`, username: `role-${r}`, password: "staff-dev-pass" })),
    { session: "relief", username: "relief", password: process.env.RELIEF_PASSWORD ?? "relief-dev-pass" },
];

test.setup("sign in as each role and as the relief worker", { sessions: logins.map((l) => l.session) }, async ({ app, screen, session }) => {
    const started = Date.now();
    for (const l of logins) {
        await app.clearState();
        await app.open("/");
        await screen.getByLabel("Username").fill(l.username);
        await screen.getByLabel("Password").fill(l.password);
        await screen.getByRole("button", "Sign in").tap();
        await expect(screen.getByRole("navigation", "Main")).toBeVisible();
        await session.save(l.session);
    }
    // These sign-ins nearly fill the server's window of 10 a minute from one address, so a test that signs in
    // next would get "Too many tries". Wait the window out.
    await new Promise((done) => setTimeout(done, Math.max(0, started + 61_000 - Date.now())));
});
