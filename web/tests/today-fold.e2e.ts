import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere } from "./support";

// Feedback 77b8ea5f: on a phone, Who's clocked in and Booked visits sit above today's sessions and
// crowd them out. On a phone they start folded, showing only their counts, and open with a tap.
const memberButtons = /^Clock (in|out) \S/;

test("on a phone, Who's clocked in and Booked visits start folded and open with a tap", { session: "owner" }, async ({ app, screen, browser }) => {
    await browser.setViewport({ width: 375, height: 812 });
    await app.open("/#/today");
    await clockInHere(screen, browser);
    await expect(screen.getByRole("button", "Who's clocked in")).toBeVisible();
    await expect(screen.getByText(/\d+ of \d+ in/)).toBeVisible();
    await expect(screen.getByRole("button", memberButtons)).toHaveCount(0);
    await expect(screen.getByText("No visits booked.")).toHaveCount(0);

    await screen.getByRole("button", "Who's clocked in").tap();
    await expect(screen.getByRole("button", memberButtons).first()).toBeVisible();
    await screen.getByRole("button", "Booked visits").tap();
    await expect(screen.getByText("No visits booked.")).toBeVisible();
});

test("on a wide screen, Who's clocked in and Booked visits stay open", { session: "owner" }, async ({ app, screen, browser }) => {
    await browser.setViewport({ width: 1280, height: 800 });
    await app.open("/#/today");
    await clockInHere(screen, browser);
    await expect(screen.getByRole("button", memberButtons).first()).toBeVisible();
    await expect(screen.getByText("No visits booked.")).toBeVisible();
});

// Feedback ef1cfb99: "I want to be able to minimize the who's clocked in and booked visits by tapping any part of it.
// Not just the text."
test("on a phone, tapping anywhere on the header row folds and unfolds the block", { session: "owner" }, async ({ app, screen, browser }) => {
    await browser.setViewport({ width: 375, height: 812 });
    await app.open("/#/today");
    await clockInHere(screen, browser);
    await expect(screen.getByRole("button", memberButtons)).toHaveCount(0);

    await screen.getByText(/\d+ of \d+ in/).tap();
    await expect(screen.getByRole("button", memberButtons).first()).toBeVisible();
    await screen.getByText(/\d+ of \d+ in/).tap();
    await expect(screen.getByRole("button", memberButtons)).toHaveCount(0);
});
