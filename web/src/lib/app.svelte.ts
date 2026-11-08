import { api, ok, setToken, getToken, setUnauthorizedHandler, getViewAs, setViewAs, type S, type ViewAs } from "../api/client";
import { forgetSnapshot } from "./snapshot";
import { key } from "./keys";
import { wrote, clearCache, peek, restoreCache, seed } from "./cache.svelte";
import { allowDirectory, refreshDirectory, resetDirectory } from "./clientdir.svelte";
import { RECONNECTED } from "./net.svelte";
import { SENT } from "./outbox.svelte";
import { allows, reads, NO_RIGHTS, type Act, type Place, type Rights } from "./rights";
import { today, takeDay, takeLists, restoreDay, resetDay, catchUpDay } from "./today.svelte";

export const app = $state({
    ready: false,
    signedIn: !!getToken(),
    me: null as S["MeResponse"] | null,
    rights: NO_RIGHTS as Rights,
    roles: [] as string[],
    branches: [] as S["MeBranchResponse"][],
    accessible: [] as S["BranchResponse"][],
    branchId: localStorage.getItem("branchId") ?? "",
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
    resetDay();
    app.unread = 0;
    location.hash = "#/today";
    await boot();
}

setUnauthorizedHandler(() => signOut(false));

export async function signIn(username: string, password: string) {
    const withToday = onToday();
    // One request signs in and brings everything the app needs to open.
    const r = await ok(api.POST("/auth/login", { body: { username, password, branchId: app.branchId || undefined, withToday } }));
    setToken(r.token);
    // Wait for the person's access before showing a page; else Shell lands on Relief first.
    app.ready = false;
    app.signedIn = true;
    if (!r.start) return boot();
    restoreCache();
    takeStart(r.start, withToday);
    app.ready = true;
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
    forgetSnapshot();
    resetDirectory();
    setToken(null);
    Object.assign(app, {
        signedIn: false,
        ready: true,
        me: null,
        rights: NO_RIGHTS,
        branches: [],
        accessible: [],
        unread: 0,
    });
    resetDay();
}

export const onToday = () => ["", "#", "#/", "#/today"].includes(location.hash);

/** Take in the start bundle: who, rights, branches, the branch and its day, the open shift, unread alerts, Today's lists. */
export function takeStart(s: S["StartResponse"], withToday: boolean) {
    Object.assign(app, { me: s.me, rights: s.rights, branches: s.branches, accessible: s.accessible });
    seed(key.boot("me"), s.me);
    seed(key.boot("rights"), s.rights);
    seed(key.boot("branches"), s.branches);
    seed(key.boot("accessible"), s.accessible);
    if (s.branchId && s.branchId !== app.branchId) {
        app.branchId = s.branchId;
        localStorage.setItem("branchId", s.branchId);
    }
    takeDay(s, withToday);
    app.unread = s.unreadCount;
    allowDirectory(canRead("WORK_ANYWHERE", {}));
    void refreshDirectory();
}

/** Load identity, rights and branches; pick a branch. What the phone saved shows at once. */
export async function boot() {
    if (!getToken()) {
        app.ready = true;
        return;
    }
    restoreCache();
    const saved = [peek(key.boot("me")), peek(key.boot("rights")), peek(key.boot("branches")), peek(key.boot("accessible"))];
    if (saved.every((x) => x !== undefined)) {
        Object.assign(app, { me: saved[0], rights: saved[1], branches: saved[2], accessible: saved[3] });
        restoreDay();
        app.ready = true;
    }
    try {
        // One request for all of it. Opening on Today, the page asks for its lists at once: they come in the same answer.
        const asked = app.branchId;
        const withToday = onToday();
        const start = ok(api.GET("/api/start", { params: { query: { branchId: asked || undefined, withToday } } }));
        if (withToday && asked)
            takeLists(
                asked,
                start.then((s) => {
                    if (s.branchId !== asked || !s.todayScreen) throw new Error("another branch");
                    return s.todayScreen;
                }),
            );
        const s = await start;
        takeStart(s, withToday && s.branchId !== asked);
    } finally {
        app.ready = true;
    }
}

// Opened with no signal: finish starting up once the connection is back; else catch up.
const catchUp = () => {
    if (!getToken()) return;
    if (!app.me) void boot().catch(() => {});
    else void Promise.all([catchUpDay(), refreshUnread()]).catch(() => {});
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

export async function refreshRights() {
    [app.rights, app.branches] = await Promise.all([ok(api.GET("/api/me/capabilities")), ok(api.GET("/api/me/branches"))]);
    wrote("rights");
}

export async function refreshUnread() {
    try {
        app.unread = (await ok(api.GET("/api/notifications/unread-count"))).unreadCount;
    } catch {
        /* ignore */
    }
}

/** A branch, with today's day when it is the branch on screen. */
export function at(branch: string = app.branchId): Place {
    return { branch, day: branch === app.branchId ? today.day?.branchDayId : undefined };
}
/** Would the server let the user do `act` at `place` (default: here, today)? Screens show a control only when it would. */
export function can(act: Act, place: Place = at()): boolean {
    return allows(app.rights, act, place);
}
/** Would the server answer a GET of `act`'s routes (doers, plus whoever may do its reading Act)? */
export function canRead(act: Act, place: Place = at()): boolean {
    return reads(app.rights, act, place);
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
