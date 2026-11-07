import { readFileSync } from "node:fs";
import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { FLOORS, PHONE, phoneProblems } from "./phone-check";

// Pages drawn before the phone rules. A rebuilt page leaves this list in the same change and must then fit for every role.
const LEGACY: readonly string[] = JSON.parse(readFileSync("tests/phone-legacy.json", "utf8"));
const ROLES = ["practitioner", "coordinator", "manager", "owner", "accountant", "superuser", "relief", "newcomer"] as const;
const route = (href: string) => href.split("?")[0];

for (const role of ROLES) {
    test(`every page a ${role} opens fits a ${PHONE.width}px phone`, { session: `role-${role}` }, async ({ app, screen, browser }) => {
        await browser.setViewport({ ...PHONE });
        await app.open("/#/today");
        await expect(screen.getByRole("main")).toBeVisible();
        const menu = await browser.evaluate(() =>
            [...document.querySelectorAll('nav[aria-label="Main"] a[href^="#/"]')].map((a) => a.getAttribute("href") ?? ""),
        );
        const failing: string[] = [];
        for (const href of [...new Set(["#/today", ...menu].map(route))]) {
            await app.open(`/${href}`);
            await expect(screen.getByRole("main")).toBeVisible();
            await expect(
                screen
                    .getByRole("main")
                    .getByText(/^(Loading…|Slow connection…)$/)
                    .first(),
            ).toBeHidden();
            const problems = await browser.evaluate(phoneProblems, { ...FLOORS });
            if (problems.length && !LEGACY.includes(href)) failing.push([href, ...problems.slice(0, 15)].join("\n    "));
        }
        expect(failing).toEqual([]);
    });
}

test("the phone check names small text, small targets, zooming inputs, sideways scroll and wrapped bars", async ({ app, browser }) => {
    await browser.setViewport({ ...PHONE });
    await app.open("/");
    type Part = [tag: string, css: Record<string, string>, text: string | null, oneRow: boolean];
    // Built with DOM calls: the app's CSP refuses inline style attributes, not styles set from script.
    const draw = (parts: Part[]) =>
        browser.evaluate(
            ([body, list]) => {
                document.body.replaceChildren();
                document.body.style.cssText = `margin:0;font-size:${body}`;
                for (const [tag, css, text, oneRow] of list) {
                    const el = document.createElement(tag);
                    Object.assign(el.style, css);
                    if (oneRow) {
                        el.dataset.oneRow = "";
                        for (const t of ["a", "b"]) {
                            const child = document.createElement("span");
                            Object.assign(child.style, { display: "inline-block", width: "80px" });
                            child.textContent = t;
                            el.append(child);
                        }
                    } else if (text) el.textContent = text;
                    document.body.append(el);
                }
            },
            [parts === bad ? "13px" : "15px", parts] as [string, Part[]],
        );
    const bad: Part[] = [
        ["div", { width: "500px" }, "wide", false],
        ["span", { fontSize: "11px" }, "tiny", false],
        ["input", { fontSize: "13px" }, null, false],
        ["button", { height: "26px" }, "Go", false],
        ["div", { width: "100px" }, null, true],
    ];
    await draw(bad);
    const found = await browser.evaluate(phoneProblems, { ...FLOORS });
    expect(found.map((p) => p.split(":")[0].replace(/\d+(x\d+)?(px)?/g, "N"))).toEqual([
        "scrolls sideways",
        "body text N, under N",
        "text N, under N",
        "input text N, under N (the phone zooms in)",
        "tap target N, under N",
        "tap target N, under N",
        "bar wraps to a second row",
    ]);
    const good: Part[] = [
        ["p", {}, "Fine", false],
        ["input", { fontSize: "16px", height: "44px", width: "200px" }, null, false],
        ["button", { height: "44px", minWidth: "44px", fontSize: "15px" }, "Go", false],
        ["div", { display: "flex", gap: "8px" }, null, true],
    ];
    await draw(good);
    expect(await browser.evaluate(phoneProblems, { ...FLOORS })).toEqual([]);
});

test("every page on the legacy phone list still exists in the menu", { session: "role-superuser" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await expect(screen.getByRole("main")).toBeVisible();
    const menu = await browser.evaluate(() =>
        [...document.querySelectorAll('nav[aria-label="Main"] a[href^="#/"]')].map((a) => (a.getAttribute("href") ?? "").split("?")[0]),
    );
    expect(LEGACY.filter((r) => r !== "#/today" && !menu.includes(r))).toEqual([]);
});
