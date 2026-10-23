import { ApiError, api, ok, setToken, getToken, setUnauthorizedHandler, uuid, errText, getViewAs, setViewAs, type S, type ViewAs } from "../api/client";
import { clearCache, fetchCached, forget, invalidate, peek, restoreCache } from "./cache.svelte";
import { refreshDirectory, resetDirectory } from "./clientdir.svelte";
import { RECONNECTED } from "./net.svelte";
import { doneText, heldClock, heldSoFar, SENT } from "./outbox.svelte";
import { operationalDate } from "./format";

type Cap = S["UserCapabilityResponse"];

export const app = $state({
    ready: false,
    signedIn: !!getToken(),
    me: null as S["MeResponse"] | null,
    caps: [] as Cap[],
    roles: [] as string[],
    branches: [] as S["MeBranchResponse"][],
    accessible: [] as S["BranchResponse"][],
    branchId: localStorage.getItem("branchId") ?? "",
    today: null as S["BranchDayTodayResponse"] | null,
    shift: null as S["ActiveShiftResponse"] | null,
    /** False until the first shift check answers, so the clock button never shows a guess. */
    shiftKnown: false,
    unread: 0,
    toast: null as { text: string; kind: "ok" | "bad" } | null,
    /** Set while a developer is viewing the app as someone else. */
    viewAs: getViewAs() as ViewAs | null,
});

const VIEW_AS_MINUTES = 30;

/** Developer only: see the app exactly as another person does (look-only unless changes are allowed). */
export async function startViewAs(userId: string) {
    const r = await ok(api.POST("/api/view-as", { body: { userId, allowChanges: false } }));
    const v: ViewAs = {
        userId: r.userId,
        name: r.displayName,
        roles: r.roles,
        allowChanges: false,
        until: Date.now() + VIEW_AS_MINUTES * 60_000,
        realName: app.me?.displayName ?? "",
    };
    await switchIdentity(v);
}

export async function stopViewAs(event: "stopped" | "timed out" = "stopped") {
    const v = app.viewAs;
    if (!v) return;
    await switchIdentity(null);
    await api.POST("/api/view-as/stop", { body: { userId: v.userId, event } }).catch(() => {});
}

export async function setViewAsChanges(allow: boolean) {
    const v = app.viewAs;
    if (!v) return;
    const next = { ...v, allowChanges: allow };
    setViewAs(next);
    app.viewAs = next;
    await api.POST("/api/view-as/stop", { body: { userId: v.userId, event: allow ? "changes allowed" : "changes off" } }).catch(() => {});
}

async function switchIdentity(v: ViewAs | null) {
    setViewAs(v);
    app.viewAs = v;
    clearCache();
    Object.assign(app, { today: null, shift: null, shiftKnown: false, unread: 0 });
    location.hash = "#/today";
    await boot();
}

setUnauthorizedHandler(() => signOut(false));

export async function signIn(username: string, password: string) {
    const r = await ok(api.POST("/auth/login", { body: { username, password } }));
    setToken(r.token);
    app.signedIn = true;
    await boot();
}

export async function signOut(callServer = true) {
    setViewAs(null);
    app.viewAs = null;
    if (callServer && getToken()) {
        try {
            await api.POST("/api/auth/logout");
        } catch {
            /* best effort */
        }
    }
    clearCache();
    resetDirectory();
    setToken(null);
    Object.assign(app, {
        signedIn: false,
        ready: true,
        me: null,
        caps: [],
        branches: [],
        accessible: [],
        today: null,
        shift: null,
        shiftKnown: false,
        unread: 0,
    });
}

type BootData = [S["MeResponse"], Cap[], S["MeBranchResponse"][], S["BranchResponse"][]];

function applyBoot([me, caps, branches, accessible]: BootData) {
    app.me = me;
    app.caps = caps;
    app.branches = branches;
    app.accessible = accessible;
}

/** Load identity, capabilities and branches; pick a branch. What the phone saved shows at once. */
export async function boot() {
    if (!getToken()) {
        app.ready = true;
        return;
    }
    restoreCache();
    const saved = [peek("boot:me"), peek("boot:caps"), peek("boot:branches"), peek("boot:accessible")];
    if (saved.every((x) => x !== undefined)) {
        applyBoot(saved as BootData);
        app.today = peek<S["BranchDayTodayResponse"]>(`boot:today:${app.branchId}:${operationalDate()}`) ?? null;
        app.shift = peek<S["ActiveShiftResponse"] | null>("boot:shift") ?? null;
        applyHeldClock();
        app.ready = true;
    }
    try {
        const fresh = await Promise.all([
            fetchCached("boot:me", () => ok(api.GET("/api/me")), true),
            fetchCached("boot:caps", () => ok(api.GET("/api/me/capabilities")), true),
            fetchCached("boot:branches", () => ok(api.GET("/api/me/branches")), true),
            fetchCached("boot:accessible", () => ok(api.GET("/api/branches/accessible")).catch(() => [] as S["BranchResponse"][]), true),
        ]);
        applyBoot(fresh);
        const [, , branches, accessible] = fresh;
        const known = new Set([...branches.map((b) => b.branchId), ...accessible.map((b) => b.id)]);
        if (!known.has(app.branchId)) {
            const here = branches.find((b) => b.clockInStatus === "CLOCKED_IN_HERE");
            selectBranch(here?.branchId ?? branches[0]?.branchId ?? accessible[0]?.id ?? "");
        } else {
            await refreshBranchDay();
        }
        void refreshShift();
        void refreshUnread();
        void refreshDirectory();
    } finally {
        app.ready = true;
    }
}

// Opened with no signal: finish starting up once the connection is back; else catch up.
const catchUp = () => {
    if (!getToken()) return;
    if (!app.me) void boot().catch(() => {});
    else void Promise.all([refreshBranchDay(), refreshShift(), refreshUnread()]).catch(() => {});
};

/** End a timed "view as" when it runs out, and catch up when the signal returns or held saves go out. */
export function watchSession() {
    setInterval(() => {
        if (app.viewAs && app.viewAs.until <= Date.now()) void stopViewAs("timed out");
    }, 15_000);
    window.addEventListener(RECONNECTED, catchUp);
    window.addEventListener(SENT, catchUp);
}

/** Re-read branch names after a rename (picker and top bar). */
export async function refreshBranchNames() {
    const [branches, accessible] = await Promise.all([
        ok(api.GET("/api/me/branches")).catch(() => app.branches),
        ok(api.GET("/api/branches/accessible")).catch(() => app.accessible),
    ]);
    app.branches = branches;
    app.accessible = accessible;
}

export function selectBranch(id: string) {
    app.branchId = id;
    localStorage.setItem("branchId", id);
    app.today = null;
    void refreshBranchDay();
}

export async function refreshBranchDay() {
    if (!app.branchId) return;
    const id = app.branchId;
    // The server shows a branch's day only to its staff (or a relief grant for a day);
    // skip the request for branches the person merely oversees.
    if (!app.caps.some((c) => c.contextType === "BRANCH_DAY" || (c.contextType !== "GLOBAL" && c.contextId === id))) {
        app.today = null;
        return;
    }
    const key = `boot:today:${id}:${operationalDate()}`;
    if (!app.today) app.today = peek<S["BranchDayTodayResponse"]>(key) ?? null;
    try {
        const t = await fetchCached(key, () => ok(api.GET("/api/branches/{branchId}/today", { params: { path: { branchId: id } } })), true);
        if (app.branchId === id) app.today = t;
    } catch (e) {
        // No signal: keep the day we knew.
        if (app.branchId === id && !(e instanceof ApiError && e.status <= 0)) app.today = null;
    }
}

export async function refreshShift() {
    try {
        const r = await ok(api.GET("/api/me/active-attendance"));
        app.shift = (r.shift as S["ActiveShiftResponse"] | null) ?? null;
        void fetchCached("boot:shift", () => Promise.resolve(app.shift), true);
        applyHeldClock();
    } catch (e) {
        // Lost signal says nothing about the shift: keep what we knew.
        if (!(e instanceof ApiError && e.status <= 0)) app.shift = null;
    }
    app.shiftKnown = true;
}

/** A clock-in or -out still waiting on this phone counts already, so the button and pages match. */
function applyHeldClock() {
    const h = heldClock();
    if (h === "out") app.shift = null;
    else if (h && app.shift?.attendanceId !== h.attendanceId)
        app.shift = {
            attendanceId: h.attendanceId,
            branchId: h.branchId,
            branchName: branchName(h.branchId),
            branchDayId: app.branchId === h.branchId && app.today ? app.today.branchDayId : "",
            date: new Date().toLocaleDateString("en-CA"),
            isRelief: false,
        };
}

export async function refreshCaps() {
    app.caps = await ok(api.GET("/api/me/capabilities"));
    app.branches = await ok(api.GET("/api/me/branches"));
    invalidate("");
}

export async function refreshUnread() {
    try {
        app.unread = (await ok(api.GET("/api/notifications/unread-count"))).unreadCount;
    } catch {
        /* ignore */
    }
}

/** Does the user hold `code` for the branch (or globally)? */
export function can(code: string, branchId: string = app.branchId, branchDayId?: string): boolean {
    return app.caps.some(
        (c) =>
            c.capabilityCode === code &&
            (c.contextType === "GLOBAL" ||
                ((c.contextType === "BRANCH" || c.contextType === "MEDICAL_MISSION" || c.contextType === "PROVINCIAL_TOUR") && c.contextId === branchId) ||
                (c.contextType === "BRANCH_DAY" && (!branchDayId || c.contextId === branchDayId) && c.contextId === (branchDayId ?? app.today?.branchDayId))),
    );
}
/** Rights the server only honours at a branch the person works at (an all-branch grant doesn't count). */
export function canAtBranch(code: string, branchId: string = app.branchId): boolean {
    return app.caps.some((c) => c.capabilityCode === code && c.contextType !== "GLOBAL" && c.contextId === branchId);
}
export function canAnywhere(code: string): boolean {
    return app.caps.some((c) => c.capabilityCode === code);
}

/** Adding sessions and sales needs an open shift at this branch today (no one is exempt). */
export function onDutyAt(branchId: string = app.branchId): boolean {
    return !!app.shift && app.shift.branchId === branchId && app.shift.date === operationalDate();
}

export function branchName(id: string): string {
    return app.branches.find((b) => b.branchId === id)?.branchName ?? app.accessible.find((b) => b.id === id)?.name ?? "";
}

let toastTimer: ReturnType<typeof setTimeout> | undefined;
export function toast(text: string, kind: "ok" | "bad" = "ok") {
    app.toast = { text, kind };
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => (app.toast = null), kind === "bad" ? 6000 : 2500);
}

/** After your own clock-in or -out: re-read the shift, rights and day, and reload Today's list and roster,
 *  which the old state allowed or refused. */
export async function shiftChanged() {
    await Promise.all([refreshShift(), refreshCaps()]);
    await refreshBranchDay();
    forget("dash:", "roster:");
}

export async function clockIn(branchId: string = app.branchId): Promise<boolean> {
    if (!confirm(`Clock in at ${branchName(branchId)}?`)) return false;
    const before = heldSoFar();
    try {
        await ok(api.POST("/api/attendance/clock-in", { body: { attendanceId: uuid(), branchId } }));
        // No signal: it's waiting on the phone; show the shift as started meanwhile.
        if (heldSoFar() > before) return (applyHeldClock(), (app.shiftKnown = true), toast(doneText(before)!), true);
        await shiftChanged();
        toast(doneText(before, "Clocked in")!);
        return true;
    } catch (e) {
        toast(errText(e), "bad");
        return false;
    }
}

export async function clockOut(): Promise<boolean> {
    if (!app.shift || !confirm(`Clock out of ${app.shift.branchName}?`)) return false;
    const before = heldSoFar();
    try {
        await ok(api.POST("/api/attendance/clock-out", { body: { attendanceId: app.shift.attendanceId } }));
        if (heldSoFar() > before) return (applyHeldClock(), forget("dash:", "roster:"), toast(doneText(before)!), true);
        // Today's list needs a clock-in: this takes it off screen (and off the phone) at once.
        await shiftChanged();
        toast(doneText(before, "Clocked out")!);
        return true;
    } catch (e) {
        toast(errText(e), "bad");
        return false;
    }
}
