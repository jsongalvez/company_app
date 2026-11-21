import { test } from "@e2e-dev/web";
import { expect } from "e2e";
import { businessDate } from "./clock";
import { BASE, SERVER, cut, owner, pickBranch, sleep, waitingOnPhone, workDay, expireSignIn, branchIdOf } from "./offline";

// The owner's bar (2026-11-20): a full day of work on bad wifi or with no signal, and after the signal is
// back the server's totals equal the same day worked online. Each person works one branch online and one
// cut off (tools/seed/offline-day.sql), then the two branches' totals are compared. "Flaky" is bad wifi:
// some saves reach the server but their answer is lost, some answer after the phone stopped waiting,
// some fail outright, and reads are slow.

/** Whether the person named was paid today at the branch. */
async function paid(branchId: string, name: string): Promise<boolean> {
    const auth = { headers: { Authorization: `Bearer ${await owner()}` } };
    const day = await fetch(`${BASE}/api/branches/${branchId}/daily-summary?date=${businessDate()}`, auth);
    if (day.status !== 200) return false;
    const { branchDayId } = (await day.json()) as { branchDayId: string };
    const pay = (await (await fetch(`${BASE}/api/compensations?branchDayId=${branchDayId}`, auth)).json()) as { userName?: string | null }[];
    return pay.some((c) => c.userName === name);
}

/** What the server holds for a branch's day: money, sessions, commission, shifts. */
async function totals(branchId: string) {
    const token = await owner();
    const get = async <T>(path: string): Promise<T> => {
        const res = await fetch(`${BASE}${path}`, { headers: { Authorization: `Bearer ${token}` } });
        if (res.status !== 200) throw new Error(`${path}: ${res.status} ${await res.text()}`);
        return (await res.json()) as T;
    };
    const date = businessDate();
    const sum = await get<{
        branchDayId: string;
        grossIncome: string;
        totalCompensation: string;
        totalExpenses: string;
        netIncome: string;
        totalProductSales: string;
    }>(`/api/branches/${branchId}/daily-summary?date=${date}`);
    const sessions = await get<{ sessionType: string; sessionStatus: string; finalPrice: string; isVoided: boolean }[]>(
        `/api/branches/${branchId}/sessions?date=${date}`,
    );
    const splits = await get<{ amount: string }[]>(`/api/commission-splits/${sum.branchDayId}`);
    const users = await get<unknown[]>(`/api/branch-days/${sum.branchDayId}/users`);
    const { branchDayId: _, ...money } = sum;
    return {
        money,
        sessions: sessions.map((s) => `${s.sessionType} ${s.sessionStatus} ${s.finalPrice} ${s.isVoided}`).sort(),
        splits: splits.map((s) => s.amount).sort(),
        onDuty: users.length,
    };
}

const people = [
    { who: "practitioner", session: "offline-practitioner", letter: "P", payee: false },
    { who: "coordinator", session: "offline-coordinator", letter: "C", payee: true },
] as const;

for (const p of people) {
    for (const net of ["offline", "flaky"] as const) {
        test(
            `a ${p.who}'s day ${net === "offline" ? "with no signal" : "on bad wifi"} ends with the same totals as online`,
            { session: p.session, timeout: 300_000 },
            async ({ app, screen, browser }) => {
                // Each run adds to both branches' day, so compare what this run added, not the totals.
                await pickBranch(app, screen, browser, `Desk ${p.letter} Branch`);
                const online = await branchIdOf(browser);
                const onlineBefore = await totals(online).catch(() => null);
                // Pay is one per person and day: a second run on the same day (only on this box) skips it at both branches.
                const name = net === "offline" ? "Payee cut" : "Payee flaky";
                const payee = p.payee && !(await paid(online, name)) ? name : null;
                await workDay(app, screen, browser, payee);
                const onlineAfter = await totals(online);

                await pickBranch(app, screen, browser, `Field ${p.letter} Branch`);
                const offline = await branchIdOf(browser);
                const offlineBefore = await totals(offline).catch(() => null);
                // Give the branch pull a moment to land, as a phone does between sign-in and walking out of signal.
                await sleep(2_000);
                await cut(browser, net);
                const cutAt = Date.now();
                await workDay(app, screen, browser, payee);
                if (net === "offline") {
                    // Hours pass: the sign-in runs out, and the app is closed and opened again with no signal.
                    await expireSignIn(browser);
                    await app.open("/#/today");
                    await expect(screen.getByText(/waiting to send/)).toBeVisible({ timeout: 20_000 });
                }
                await sleep(15_000);
                const backAt = Date.now();
                await browser.unroute(SERVER);
                await expect.poll(() => waitingOnPhone(browser), { timeout: 120_000 }).toBe(0);
                await expect(screen.getByText("couldn't be sent", { exact: false })).toBeHidden();
                const offlineAfter = await totals(offline);

                expect(delta(offlineBefore, offlineAfter)).toEqual(delta(onlineBefore, onlineAfter));

                // The shift counts from when it began on the phone, not from when the phone was back.
                const token = await browser.evaluate(() => localStorage.getItem("token") ?? "");
                const history = (await (
                    await fetch(`${BASE}/api/me/history?from=${businessDate()}&to=${businessDate()}`, { headers: { Authorization: `Bearer ${token}` } })
                ).json()) as { shifts: { branchName: string; clockIn: string }[] };
                const shifts = history.shifts.filter((s) => s.branchName === `Field ${p.letter} Branch`).map((s) => Date.parse(s.clockIn));
                expect(shifts.length).toBeGreaterThan(0);
                const last = Math.max(...shifts);
                expect(last).toBeGreaterThanOrEqual(cutAt - 2_000);
                expect(last).toBeLessThan(backAt - 10_000);
            },
        );
    }
}

type Totals = Awaited<ReturnType<typeof totals>>;
/** What a run added to a branch's day: money as amounts, the rest as counts and lists of what is new. */
function delta(before: Totals | null, after: Totals) {
    const num = (v: string | undefined) => Number(v ?? 0);
    const added = (b: string[] = [], a: string[]) => {
        const left = [...b];
        return a.filter((x) => (left.includes(x) ? (left.splice(left.indexOf(x), 1), false) : true));
    };
    return {
        money: Object.fromEntries(Object.entries(after.money).map(([k, v]) => [k, (num(v) - num(before?.money[k as keyof Totals["money"]])).toFixed(2)])),
        sessions: added(before?.sessions, after.sessions),
        splits: (num(after.splits.reduce((s, x) => String(num(s) + num(x)), "0")) - num(before?.splits.reduce((s, x) => String(num(s) + num(x)), "0"))).toFixed(
            2,
        ),
        onDuty: after.onDuty - (before?.onDuty ?? 0),
    };
}
