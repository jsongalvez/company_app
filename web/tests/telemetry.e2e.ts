import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { hash } from "../src/lib/telemetry";
import { doneSessionForNewClient } from "./support";

type Sent = { sessionId: string; events: { kind: string; data: string }[] };

test("what a person does is uploaded without names or typed text", { session: "owner" }, async ({ app, screen, browser }) => {
    await app.open("/#/today");
    await browser.evaluate(() => {
        const w = window as unknown as { __sent: string[] };
        w.__sent = [];
        const real = window.fetch;
        window.fetch = (input, init) => {
            if (String(input).includes("/api/telemetry")) w.__sent.push(String(init?.body));
            return real(input, init);
        };
    });
    const name = await doneSessionForNewClient(app, screen, browser);
    await browser.evaluate(() => dispatchEvent(new Event("pagehide")));

    let sent: Sent[] = [];
    await expect
        .poll(async () => {
            sent = (await browser.evaluate(() => (window as unknown as { __sent: string[] }).__sent)).map((b) => JSON.parse(b) as Sent);
            return sent.length;
        })
        .toBeGreaterThan(0);
    const raw = JSON.stringify(sent);
    for (const part of name.split(" ")) expect(raw).not.toContain(part);

    const events = sent.flatMap((b) => b.events.map((e) => ({ kind: e.kind, ...(JSON.parse(e.data) as Record<string, unknown>) })));
    const taps = events.filter((e) => e.kind === "tap").map((e) => (e.el as { h?: string }).h);
    expect(taps).toContain(hash("New session"));
    expect(events.some((e) => e.kind === "api" && e.m === "POST" && e.p === "/api/sessions" && Number(e.s) < 300)).toBe(true);
    const ageField = events.find((e) => e.kind === "input" && e.type === "number" && (e.el as { path: string }).path.includes("label"));
    expect(ageField?.val).toBeUndefined();
});

test("each request's network timing and the tab leaving and coming back are uploaded", { session: "owner" }, async ({ app, browser }) => {
    await app.open("/#/today");
    await browser.evaluate(() => {
        const w = window as unknown as { __sent: string[] };
        w.__sent = [];
        const real = window.fetch;
        window.fetch = (input, init) => {
            if (String(input).includes("/api/telemetry")) w.__sent.push(String(init?.body));
            return real(input, init);
        };
        const show = (state: "hidden" | "visible") => {
            Object.defineProperty(document, "visibilityState", { value: state, configurable: true });
            document.dispatchEvent(new Event("visibilitychange"));
        };
        show("hidden");
        show("visible");
        show("hidden");
    });

    let events: Record<string, unknown>[] = [];
    await expect
        .poll(async () => {
            const bodies = await browser.evaluate(() => {
                dispatchEvent(new Event("pagehide"));
                return (window as unknown as { __sent: string[] }).__sent;
            });
            events = bodies.flatMap((b) =>
                (JSON.parse(b) as { events: { kind: string; data: string }[] }).events.map((e) => ({
                    kind: e.kind,
                    ...(JSON.parse(e.data) as Record<string, unknown>),
                })),
            );
            return events.some((e) => e.kind === "vis" && e.st === "visible");
        })
        .toBe(true);
    const net = events.filter((e) => e.kind === "net" && String(e.p).startsWith("/api/"));
    expect(net.length).toBeGreaterThan(0);
    expect(net.every((e) => typeof e.proto === "string" && typeof e.ttfb === "number")).toBe(true);
    expect(typeof events.find((e) => e.kind === "vis" && e.st === "visible")?.away).toBe("number");
});
