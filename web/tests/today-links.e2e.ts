import { test } from "@e2e-dev/web";
import { expect } from "e2e";

// Feedback 7df5db8c: blue link text on Today ("Product sales" inside a grey label) looked out of place.
// Links read as the text around them; the Product sales tile as a whole opens the day's sales.
test("Today's links aren't blue text; the Product sales tile opens the day's sales", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    const colors = await browser.evaluate(() => {
        const accent = getComputedStyle(document.documentElement).getPropertyValue("--accent").trim();
        const probe = document.createElement("span");
        probe.style.color = accent;
        document.body.append(probe);
        const blue = getComputedStyle(probe).color;
        probe.remove();
        return [...document.querySelectorAll("main a")].map((a) => ({ text: a.textContent?.trim(), blue: getComputedStyle(a).color === blue }));
    });
    expect(colors.filter((c) => c.blue)).toEqual([]);
    await screen.getByRole("link", { name: /Product sales/ }).click();
    await expect.poll(() => browser.evaluate(() => location.hash)).toMatch(/^#\/finance\?date=/);
});
