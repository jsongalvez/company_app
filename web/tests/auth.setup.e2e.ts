import { test } from "@e2e-dev/web";
import { credentials } from "e2e";
import { signIn } from "./support";

test.setup("sign in as owner", { sessions: ["owner"] }, async ({ app, screen, session }) => {
    const owner = credentials.user("owner");
    await signIn(app, screen, owner.username, owner.password);
    await session.save("owner");
});
