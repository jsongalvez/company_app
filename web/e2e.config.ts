import type { E2EConfig } from "e2e";
import { web } from "@e2e-dev/web";

// E2E_BROWSER=webkit|firefox runs the same tests in Safari's or Firefox's engine (chromium by default).
const browser = (process.env.E2E_BROWSER ?? "chromium") as "chromium" | "firefox" | "webkit";
// Outside Chromium, a service worker's requests skip the tests' network cuts (browser.route). Any added
// header makes the engine block service workers, so a cut there reaches every request too.
const headers = browser === "chromium" ? undefined : { "X-E2E-Browser": browser };

// Runs against a backend that serves the built web app (WEB_DIR=web/dist).
// Start it first: `./gradlew :backend:run` from the repo root, with the dev
// seed logins from .env. APP_URL overrides the address.
const engine = web({ browser, headers, viewport: { width: 1400, height: 860 }, timezoneId: "Asia/Manila" });
// The runner's handle, for tests that drive Playwright itself (tests/live.ts). A test file that imports this
// config gets a second copy of the module whose handle runs nothing, hence the first one wins.
(globalThis as { e2eEngine?: typeof engine }).e2eEngine ??= engine;

export default {
    targets: [
        {
            engine,
            app: { url: process.env.APP_URL ?? "http://localhost:18080" },
        },
    ],
    tests: ["tests/**/*.e2e.ts"],
    credentials: {
        owner: { username: "owner", password: process.env.E2E_OWNER_PASSWORD ?? "owner-dev-pass" },
    },
} satisfies E2EConfig;
