/** Hash router: #/path/segments?query. No history API needed for static hosting. */

export type Route = { path: string; parts: string[]; query: URLSearchParams };

function parse(): Route {
    const raw = location.hash.replace(/^#/, "") || "/";
    const [p = "/", q = ""] = raw.split("?");
    const path = p.startsWith("/") ? p : `/${p}`;
    return { path, parts: path.split("/").filter(Boolean), query: new URLSearchParams(q) };
}

export const route = $state<{ current: Route }>({ current: parse() });

addEventListener("hashchange", () => {
    route.current = parse();
});

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
