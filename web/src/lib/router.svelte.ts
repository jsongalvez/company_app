/** Hash router: #/path/segments?query. No history API needed for static hosting. */

export type Route = { path: string; parts: string[]; query: URLSearchParams };

function parse(): Route {
    const raw = location.hash.replace(/^#/, "") || "/";
    const [p = "/", q = ""] = raw.split("?");
    const path = p.startsWith("/") ? p : `/${p}`;
    return { path, parts: path.split("/").filter(Boolean), query: new URLSearchParams(q) };
}

export const route = $state<{ current: Route }>({ current: parse() });

// How many in-app page changes happened since the app opened, so Back knows
// whether the previous page is ours or another site.
let steps = 0;
let backing = false;

addEventListener("hashchange", (e) => {
    const from = new URL(e.oldURL).hash.split("?")[0];
    route.current = parse();
    if (backing) backing = false;
    else if (from !== location.hash.split("?")[0]) steps++;
});

/** Back to the page you came from inside the app; to `fallback` when there is none. */
export function goBack(fallback = "/today") {
    if (steps > 0 && history.length > 1) {
        steps--;
        backing = true;
        history.back();
    } else go(fallback);
}

export function go(path: string, query?: Record<string, string | null | undefined>) {
    let h = `#${path}`;
    if (query) {
        const q = new URLSearchParams();
        for (const [k, v] of Object.entries(query)) if (v) q.set(k, v);
        const s = q.toString();
        if (s) h += `?${s}`;
    }
    if (location.hash !== h) location.hash = h;
}

export function href(path: string): string {
    return `#${path}`;
}

/**
 * A view setting (tab, filter, checkbox) kept in the address, so a reload or a shared link keeps it.
 * `read` gives the saved value at page open (or the fallback); `keep` writes changes back
 * without adding a Back step, and only while the page that made it is still showing.
 */
export function viewSetting(key: string, fallback: string, allowed?: readonly string[]) {
    const path = parse().path;
    const saved = parse().query.get(key);
    const read = saved !== null && (!allowed || allowed.includes(saved)) ? saved : fallback;
    function keep(value: string) {
        const r = parse();
        if (r.path !== path) return;
        if (value && value !== fallback) r.query.set(key, value);
        else r.query.delete(key);
        const s = r.query.toString();
        const h = `#${r.path}${s ? `?${s}` : ""}`;
        if (location.hash === h) return;
        history.replaceState(history.state, "", h);
        route.current = parse();
    }
    return { read, keep };
}
