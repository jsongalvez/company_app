import { surfaceOf, type PlaywrightLiveSurface } from "@e2e-dev/web";
import { devices, type BrowserContext } from "playwright-core";

// What the e2e engine has no form for (a phone's device profile, the page's clock) is Playwright's own,
// reached through the engine handle e2e.config.ts leaves on globalThis.

/** The running attempt's Playwright page and context. */
export function live(): PlaywrightLiveSurface {
    const engine = (globalThis as { e2eEngine?: Parameters<typeof surfaceOf>[0] }).e2eEngine;
    const surface = engine && surfaceOf(engine);
    if (!surface) throw new Error("e2e.config.ts left no web engine on globalThis");
    return surface;
}

/** A fresh iPhone in the attempt's browser: iOS Safari's user agent, a 393x659 touch screen, isMobile. The caller closes it. */
export async function iPhone(init?: () => void): Promise<BrowserContext> {
    const browser = live().context().browser();
    if (!browser) throw new Error("the attempt's context has no browser");
    const { defaultBrowserType: _, ...profile } = devices["iPhone 15"];
    const context = await browser.newContext({ ...profile, timezoneId: "Asia/Manila" });
    if (init) await context.addInitScript(init);
    return context;
}
