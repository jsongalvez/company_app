import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";
import { live } from "./live";
import { BASE, SERVER, cut, owner, pickBranch, sleep, waitingOnPhone } from "./offline";
import { clockInHere, clockOutHere } from "./support";

// A shift with no signal, by the wall clock: the phone's clock is set back to 06:00 of a past day, the person
// clocks in, hours pass, the app is closed and opened again, more hours pass, they clock out; then the clock
// jumps to now and the signal comes back. The server can't be moved in time, so the phone's day lies in the
// past instead. Within 48 h (owner, 2026-11-20) the shift lands by itself on the day it was worked, at the
// phone's times, flagged for review; older, a practitioner (no right to change a closed day) gets it back as
// not sent.
//
// Only saves whose day the server takes from their time are worked here. A sale or a session names the day
// the phone pulled, which is the server's today, not the phone's past day.

const HOUR = 3_600_000;
/** 06:00 in Manila on the business date `days` from today: two hours into that day (it turns over at 04:00). */
const sixAm = (days: number) => Date.parse(`${businessDate(days)}T06:00:00+08:00`);

type Shift = { branchName: string; clockIn: string; clockOut?: string | null };
type Flagged = { tableName: string; branchName?: string | null; reason?: string | null; changedAt: string };

async function get<T>(path: string, token: string): Promise<T> {
    const res = await fetch(`${BASE}${path}`, { headers: { Authorization: `Bearer ${token}` } });
    if (res.status !== 200) throw new Error(`${path}: ${res.status} ${await res.text()}`);
    return (await res.json()) as T;
}

type Fixtures = Parameters<Parameters<typeof test>[1]>[0];

/** Clock in at `start` on the phone's clock with no signal, four hours, reopen the app, four hours, clock out; then now and signal. */
async function shiftOffline({ app, screen, browser }: Fixtures, branch: string, start: number): Promise<number> {
    const clock = live().context().clock;
    await clock.install({ time: start });
    await pickBranch(app, screen, browser, branch);
    await sleep(2_000);
    await cut(browser, "offline");
    await clock.setSystemTime(start);
    await clockInHere(screen, browser);
    await clock.fastForward(4 * HOUR);
    await app.open("/#/today");
    await expect(screen.getByText(/waiting to send/)).toBeVisible({ timeout: 20_000 });
    await clock.fastForward(4 * HOUR);
    await clockOutHere(screen, browser);
    await expect(screen.getByText(/waiting to send/)).toBeVisible({ timeout: 20_000 });

    const back = Date.now();
    await clock.setSystemTime(back);
    await browser.unroute(SERVER);
    return back;
}

test(
    "a shift worked with no signal yesterday lands on yesterday at the phone's times, flagged for review",
    { session: "late-practitioner", timeout: 240_000 },
    async (fixtures) => {
        const { screen, browser } = fixtures;
        const day = businessDate(-1);
        const start = sixAm(-1);
        const back = await shiftOffline(fixtures, "Late P Branch", start);
        await expect.poll(() => waitingOnPhone(browser), { timeout: 120_000 }).toBe(0);
        await expect(screen.getByText("couldn't be sent", { exact: false })).toBeHidden();

        const token = await browser.evaluate(() => localStorage.getItem("token") ?? "");
        const { shifts } = await get<{ shifts: Shift[] }>(`/api/me/history?from=${day}&to=${day}`, token);
        const shift = shifts.filter((s) => s.branchName === "Late P Branch").sort((a, b) => Date.parse(b.clockIn) - Date.parse(a.clockIn))[0];
        expect(shift).toBeDefined();
        expect(Math.abs(Date.parse(shift!.clockIn) - start)).toBeLessThan(5 * 60_000);
        expect(Math.abs(Date.parse(shift!.clockOut ?? "") - (start + 8 * HOUR))).toBeLessThan(5 * 60_000);

        const flagged = await get<Flagged[]>("/api/audit-log/flagged", await owner());
        const late = flagged.filter((e) => e.branchName === "Late P Branch" && e.tableName === "attendance" && Date.parse(e.changedAt) >= back - 60_000);
        expect(late.length).toBeGreaterThanOrEqual(2);
        for (const e of late) expect(e.reason).toBe(`Saved offline on ${day}, sent on ${businessDate()}`);
    },
);

test(
    "a shift saved with no signal more than 48 hours ago comes back as not sent for a practitioner",
    { session: "stale-practitioner", timeout: 240_000 },
    async (fixtures) => {
        const { screen, browser } = fixtures;
        const day = businessDate(-3);
        await shiftOffline(fixtures, "Stale P Branch", sixAm(-3));
        await expect(screen.getByText("couldn't be sent", { exact: false })).toBeVisible({ timeout: 120_000 });

        const token = await browser.evaluate(() => localStorage.getItem("token") ?? "");
        const { shifts } = await get<{ shifts: Shift[] }>(`/api/me/history?from=${day}&to=${day}`, token);
        expect(shifts.filter((s) => s.branchName === "Stale P Branch")).toEqual([]);
    },
);
