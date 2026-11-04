import { test } from "@e2e-dev/web";
import { expect } from "e2e";

type Upload = { url: string; status: number; body: string };

test("a script error on the sign-in screen reaches the server", async ({ app, browser }) => {
    await app.clearState();
    await app.open("/");
    await browser.evaluate(() => {
        const w = window as unknown as { __up: Upload[] };
        w.__up = [];
        const real = window.fetch;
        window.fetch = async (input, init) => {
            const res = await real(input, init);
            if (String(input).includes("telemetry")) w.__up.push({ url: String(input), status: res.status, body: String(init?.body) });
            return res;
        };
        setTimeout(() => {
            throw new TypeError("signed-out probe");
        });
    });

    let uploads: Upload[] = [];
    await expect
        .poll(async () => {
            uploads = await browser.evaluate(() => {
                dispatchEvent(new Event("pagehide"));
                return (window as unknown as { __up: Upload[] }).__up;
            });
            return uploads.some((u) => u.status === 204 && u.body.includes("signed-out probe"));
        })
        .toBe(true);
});
