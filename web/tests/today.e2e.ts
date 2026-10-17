import { test } from "@e2e-dev/web";
import { doneSessionForNewClient } from "./support";

test("log a session for a new client and mark it done", { session: "owner" }, async ({ app, screen }) => {
    await doneSessionForNewClient(app, screen);
});
