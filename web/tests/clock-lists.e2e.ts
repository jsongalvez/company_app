import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere, clockOutHere } from "./support";

// A clock-in or -out changes attendance; every cached list that read attendance must show the change on its
// next open, not only Today. My shifts is opened first, so its answer sits in the cache when the clock changes.
test("clocking in and out updates My shifts and Today's roster without a reload", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await clockOutHere(screen, browser);
    await expect(screen.getByText("0 of 2 in")).toBeVisible();
    const nav = screen.getByRole("navigation", "Main");
    const main = screen.getByRole("main");
    const shifts = async () => {
        await nav.getByRole("link", "My shifts & pay").tap();
        await expect(screen.getByRole("heading", "Clock-ins")).toBeVisible();
    };
    const today = async () => {
        await nav.getByRole("link", "Sessions").tap();
        await expect(screen.getByRole("heading", "Sessions")).toBeVisible();
    };

    await shifts();
    await expect(main.getByText("Still in")).toHaveCount(0);
    await today();
    await clockInHere(screen, browser);
    await expect(screen.getByText("1 of 2 in")).toBeVisible();

    await shifts();
    await expect(main.getByText("Still in")).toHaveCount(1);
    await today();
    await expect(screen.getByText("1 of 2 in")).toBeVisible();
    await clockOutHere(screen, browser);
    await expect(screen.getByText("0 of 2 in")).toBeVisible();

    await shifts();
    await expect(main.getByText("Still in")).toHaveCount(0);
});
