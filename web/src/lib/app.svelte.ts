import { api, ok, setToken, getToken, setUnauthorizedHandler, uuid, errText, type S } from "../api/client";
import { clearCache, invalidate } from "./cache.svelte";

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
});

setUnauthorizedHandler(() => signOut(false));

export async function signIn(username: string, password: string) {
    const r = await ok(api.POST("/auth/login", { body: { username, password } }));
    setToken(r.token);
    app.signedIn = true;
    await boot();
}

export async function signOut(callServer = true) {
    if (callServer && getToken()) {
        try {
            await api.POST("/api/auth/logout");
        } catch {
            /* best effort */
        }
    }
    setToken(null);
    clearCache();
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

/** Load identity, capabilities and branches; pick a branch. */
export async function boot() {
    if (!getToken()) {
        app.ready = true;
        return;
    }
    try {
        const [me, caps, branches, accessible] = await Promise.all([
            ok(api.GET("/api/me")),
            ok(api.GET("/api/me/capabilities")),
            ok(api.GET("/api/me/branches")),
            ok(api.GET("/api/branches/accessible")).catch(() => [] as S["BranchResponse"][]),
        ]);
        app.me = me;
        app.caps = caps;
        app.branches = branches;
        app.accessible = accessible;
        const known = new Set([...branches.map((b) => b.branchId), ...accessible.map((b) => b.id)]);
        if (!known.has(app.branchId)) {
            const here = branches.find((b) => b.clockInStatus === "CLOCKED_IN_HERE");
            selectBranch(here?.branchId ?? branches[0]?.branchId ?? accessible[0]?.id ?? "");
        } else {
            await refreshBranchDay();
        }
        void refreshShift();
        void refreshUnread();
    } finally {
        app.ready = true;
    }
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
    try {
        const t = await ok(api.GET("/api/branches/{branchId}/today", { params: { path: { branchId: id } } }));
        if (app.branchId === id) app.today = t;
    } catch {
        if (app.branchId === id) app.today = null;
    }
}

export async function refreshShift() {
    try {
        const r = await ok(api.GET("/api/me/active-attendance"));
        app.shift = (r.shift as S["ActiveShiftResponse"] | null) ?? null;
    } catch {
        app.shift = null;
    }
    app.shiftKnown = true;
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

export function branchName(id: string): string {
    return app.branches.find((b) => b.branchId === id)?.branchName ?? app.accessible.find((b) => b.id === id)?.name ?? "";
}

let toastTimer: ReturnType<typeof setTimeout> | undefined;
export function toast(text: string, kind: "ok" | "bad" = "ok") {
    app.toast = { text, kind };
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => (app.toast = null), kind === "bad" ? 6000 : 2500);
}

export async function clockIn(branchId: string = app.branchId): Promise<boolean> {
    try {
        await ok(api.POST("/api/attendance/clock-in", { body: { attendanceId: uuid(), branchId } }));
        await Promise.all([refreshShift(), refreshBranchDay(), refreshCaps()]);
        toast("Clocked in");
        return true;
    } catch (e) {
        toast(errText(e), "bad");
        return false;
    }
}

export async function clockOut(): Promise<boolean> {
    if (!app.shift) return false;
    try {
        await ok(api.POST("/api/attendance/clock-out", { body: { attendanceId: app.shift.attendanceId } }));
        await Promise.all([refreshShift(), refreshCaps()]);
        toast("Clocked out");
        return true;
    } catch (e) {
        toast(errText(e), "bad");
        return false;
    }
}
