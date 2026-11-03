/**
 * Today's state: the branch's day, your open shift, and a clock mark shown before the server answers.
 * Only this file changes it. Screens read `today` and call the actions below.
 */
import { ApiError, api, ok, uuid, errText, type S } from "../api/client";
import { key } from "./keys";
import { wrote, fetchCached, peek, seed } from "./cache.svelte";
import { doneText, heldClock, heldSoFar } from "./outbox.svelte";
import { operationalDate } from "./format";
import { seedTodayScreen } from "./data";
import { act } from "./act.svelte";
import { app, branchName, onToday, takeStart, toast } from "./app.svelte";

type Day = S["BranchDayTodayResponse"];
type Shift = S["ActiveShiftResponse"];
type Mark = { userId: string; present: boolean };
type TodayState = {
    day: Day | null;
    shift: Shift | null;
    /** False until the first shift check answers, so the clock button never shows a guess. */
    shiftKnown: boolean;
    /** A clock-in or -out shown before the server has answered; Today's roster shows it over what it loaded. */
    marking: Mark | null;
};

const s = $state<TodayState>({ day: null, shift: null, shiftKnown: false, marking: null });
export const today: Readonly<TodayState> = s;

/** Adding sessions and sales needs an open shift at this branch today (no one is exempt). */
export function onDutyAt(branchId: string = app.branchId): boolean {
    return !!s.shift && s.shift.branchId === branchId && s.shift.date === operationalDate();
}

/** App lifecycle, called by app.svelte.ts only (tests/unit/today-owner.test.ts). */
export function takeDay(st: S["StartResponse"], withToday: boolean) {
    s.day = st.today;
    if (st.branchId && st.today) seed(key.bootToday(st.branchId, operationalDate()), st.today);
    s.shift = (st.shift as Shift | null) ?? null;
    seed(key.boot("shift"), s.shift);
    applyHeldClock();
    s.shiftKnown = true;
    if (withToday && st.branchId && st.todayScreen) takeLists(st.branchId, st.todayScreen);
}
export function restoreDay() {
    s.day = peek<Day>(key.bootToday(app.branchId, operationalDate())) ?? null;
    s.shift = peek<Shift | null>(key.boot("shift")) ?? null;
    applyHeldClock();
}
export function resetDay() {
    Object.assign(s, { day: null, shift: null, shiftKnown: false, marking: null });
}
export function catchUpDay() {
    s.marking = null;
    return Promise.all([refreshDay(), refreshShift()]);
}

/** Today's lists that came with another answer (the start bundle, a save with `?withToday=true`): the lists show them without asking again. */
export function takeLists(branchId: string, screen: S["TodayScreenResponse"] | Promise<S["TodayScreenResponse"]>) {
    seedTodayScreen(branchId, Promise.resolve(screen));
}

export function selectBranch(id: string) {
    app.branchId = id;
    localStorage.setItem("branchId", id);
    s.day = null;
    void refreshDay();
}

export async function refreshDay() {
    if (!app.branchId) return;
    const id = app.branchId;
    // The server shows a branch's day only to its staff (or a relief grant for a day);
    // skip the request for branches the person merely oversees.
    if (!app.caps.some((c) => c.contextType === "BRANCH_DAY" || (c.contextType !== "GLOBAL" && c.contextId === id))) {
        s.day = null;
        return;
    }
    const k = key.bootToday(id, operationalDate());
    if (!s.day) s.day = peek<Day>(k) ?? null;
    try {
        const t = await fetchCached(k, () => ok(api.GET("/api/branches/{branchId}/today", { params: { path: { branchId: id } } })), true);
        if (app.branchId === id) s.day = t;
    } catch (e) {
        // No signal: keep the day we knew.
        if (app.branchId === id && !(e instanceof ApiError && e.status <= 0)) s.day = null;
    }
}

export async function refreshShift() {
    try {
        const r = await ok(api.GET("/api/me/active-attendance"));
        s.shift = (r.shift as Shift | null) ?? null;
        void fetchCached(key.boot("shift"), () => Promise.resolve(s.shift), true);
        applyHeldClock();
    } catch (e) {
        // Lost signal says nothing about the shift: keep what we knew.
        if (!(e instanceof ApiError && e.status <= 0)) s.shift = null;
    }
    s.shiftKnown = true;
}

function shiftAt(attendanceId: string, branchId: string): Shift {
    return {
        attendanceId,
        branchId,
        branchName: branchName(branchId),
        branchDayId: app.branchId === branchId && s.day ? s.day.branchDayId : "",
        date: operationalDate(),
        isRelief: false,
    };
}

/** A clock-in or -out still waiting on this phone counts already, so the button and pages match. */
function applyHeldClock() {
    const h = heldClock();
    if (h === "out") s.shift = null;
    else if (h && s.shift?.attendanceId !== h.attendanceId) s.shift = shiftAt(h.attendanceId, h.branchId);
}

/** Show a clock-in or -out the moment it's confirmed. Returns how to take it back if the server refuses. */
function showMark(userId: string, present: boolean, branchId: string, attendanceId: string | null): () => void {
    const was = { shift: s.shift, marking: s.marking };
    s.marking = { userId, present };
    if (userId === app.me?.id) s.shift = present ? shiftAt(attendanceId ?? uuid(), branchId) : null;
    return () => Object.assign(s, was);
}

/** A clock change answers with the app's new state: take it in, then the pending mark gives way to it.
 *  No state means the change is waiting on the phone; the mark stays shown until it goes out. */
async function applyStart(st: S["StartResponse"] | null | undefined) {
    if (!st) return;
    takeStart(st, onToday());
    await wrote("clockApplied");
    s.marking = null;
}

export async function clockIn(branchId: string = app.branchId): Promise<boolean> {
    if (!confirm(`Clock in at ${branchName(branchId)}?`)) return false;
    const before = heldSoFar();
    const attendanceId = uuid();
    const undo = app.me ? showMark(app.me.id, true, branchId, attendanceId) : () => {};
    try {
        const r = await ok(api.POST("/api/attendance/clock-in", { params: { query: { withToday: onToday() } }, body: { attendanceId, branchId } }));
        // No signal: it's waiting on the phone; show the shift as started meanwhile.
        if (heldSoFar() > before) return (applyHeldClock(), (s.shiftKnown = true), toast(doneText(before)!), true);
        toast(doneText(before, "Clocked in")!);
        void applyStart(r.start);
        return true;
    } catch (e) {
        undo();
        toast(errText(e), "bad");
        return false;
    }
}

export async function clockOut(): Promise<boolean> {
    if (!s.shift || !confirm(`Clock out of ${s.shift.branchName}?`)) return false;
    const before = heldSoFar();
    const { attendanceId, branchId } = s.shift;
    const undo = app.me ? showMark(app.me.id, false, branchId, null) : () => {};
    try {
        const r = await ok(
            api.POST("/api/attendance/clock-out", { params: { query: { branchId: app.branchId || undefined, withToday: onToday() } }, body: { attendanceId } }),
        );
        if (heldSoFar() > before) return (applyHeldClock(), wrote("clockOutHeld"), toast(doneText(before)!), true);
        toast(doneText(before, "Clocked out")!);
        // Today's list needs a clock-in: the new state takes it off screen (and off the phone).
        void applyStart(r.start);
        return true;
    } catch (e) {
        undo();
        toast(errText(e), "bad");
        return false;
    }
}

/** Clock a branch member in or out from Today's roster. The top bar and Today change with the answer. */
export async function markMember(userId: string, present: boolean, branchId: string): Promise<void> {
    const attendanceId = present ? uuid() : null;
    const undo = showMark(userId, present, branchId, attendanceId);
    let answer: S["AttendanceMarkResponse"] | undefined;
    const done = await act(async () => {
        answer = await ok(
            api.POST("/api/branches/{branchId}/attendance/marks", {
                params: { path: { branchId }, query: { withToday: true } },
                body: { userId, present, attendanceId },
            }),
        );
    });
    if (!done) return undo();
    await applyStart(answer?.start);
}
