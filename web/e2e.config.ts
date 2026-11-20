import type { E2EConfig } from "e2e";
import { web } from "@e2e-dev/web";

// E2E_BROWSER=webkit|firefox runs the same tests in Safari's or Firefox's engine (chromium by default).
const browser = (process.env.E2E_BROWSER ?? "chromium") as "chromium" | "firefox" | "webkit";

// Runs against a backend that serves the built web app (WEB_DIR=web/dist).
// Start it first: `./gradlew :backend:run` from the repo root, with the dev
// seed logins from .env. APP_URL overrides the address.
export default {
    targets: [
        {
            engine: web({ browser, viewport: { width: 1400, height: 860 }, timezoneId: "Asia/Manila" }),
            app: { url: process.env.APP_URL ?? "http://localhost:18080" },
        },
    ],
    tests: ["tests/**/*.e2e.ts"],
    credentials: {
        owner: { username: "owner", password: process.env.E2E_OWNER_PASSWORD ?? "owner-dev-pass" },
    },
} satisfies E2EConfig;
