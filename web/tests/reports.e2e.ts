import { readFileSync } from "node:fs";
import { test } from "@e2e-dev/web";
import { expect } from "e2e";

test("download the accountant report as Excel and PDF", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/reports");
    await expect(screen.getByText("Report for your accountant")).toBeVisible();

    const xlsx = await browser.waitForDownload(() => screen.getByRole("button", "Download Excel").tap());
    expect(xlsx.suggestedFilename).toMatch(/^report-.+\.xlsx$/);
    expect(readFileSync(xlsx.absolutePath).subarray(0, 2).toString()).toBe("PK");

    await screen.getByLabel("Branches in the report").selectOption("All branches");
    const pdf = await browser.waitForDownload(() => screen.getByRole("button", "Download PDF").tap());
    expect(pdf.suggestedFilename).toMatch(/^report-all-branches-.+\.pdf$/);
    expect(readFileSync(pdf.absolutePath).subarray(0, 4).toString()).toBe("%PDF");
});
