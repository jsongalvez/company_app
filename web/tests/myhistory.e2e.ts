import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { clockInHere } from "./support";

// Feedback a21d3409: "where can I see my clock in history and received money history".
test("my own clock-ins show on My shifts & pay", { session: "owner" }, async ({ app, screen }) => {
    await app.open("/#/today");
    await clockInHere(screen);
    await screen.getByRole("navigation", "Main").getByRole("link", "My shifts & pay").tap();
    await expect(screen.getByRole("heading", "My shifts & pay")).toBeVisible();
    await expect(screen.getByRole("heading", "Money received")).toBeVisible();
    await expect(screen.getByRole("heading", "Clock-ins")).toBeVisible();
    await expect(screen.getByText("Still clocked in").first()).toBeVisible();
});
