// Keeps the app itself on the phone, so it opens fast on weak signal and still opens with none.
// Data (/api, /auth) is never cached here: it always comes from the server.
const CACHE = "app-shell-v1";
const MAX_ASSETS = 80;

// Save the page and the files it loads right away, so the very next open works offline.
async function saveApp() {
    const res = await fetch("/", { cache: "no-store" });
    if (!res.ok) return;
    const html = await res.clone().text();
    await put("/", res);
    const files = [...html.matchAll(/(?:src|href)="(\/assets\/[^"]+)"/g)].map((m) => m[1]);
    await Promise.all(files.map((f) => fetch(f).then((r) => put(f, r))));
}

self.addEventListener("install", (e) =>
    e.waitUntil(
        saveApp()
            .catch(() => {})
            .then(() => self.skipWaiting()),
    ),
);
self.addEventListener("activate", (e) =>
    e.waitUntil(
        caches
            .keys()
            .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
            .then(() => self.clients.claim()),
    ),
);

async function put(req, res) {
    if (!res.ok || res.type !== "basic") return;
    const c = await caches.open(CACHE);
    await c.put(req, res);
    const keys = await c.keys();
    const assets = keys.filter((k) => new URL(k.url).pathname.startsWith("/assets/"));
    for (const k of assets.slice(0, Math.max(0, assets.length - MAX_ASSETS))) await c.delete(k);
}

// Page loads: the saved page at once, refreshed in the background for the next open. Waiting for the
// network first cost every open a round trip (about 340 ms on a weak hotspot) and up to 3 s on a bad one.
// The app asks the server for the current build when it starts and offers the update.
function page(e) {
    const fresh = fetch(e.request).then(async (res) => {
        if (res.ok) await put("/", res.clone());
        return res;
    });
    e.waitUntil(fresh.catch(() => {}));
    return caches.match("/").then((saved) => saved ?? fresh);
}

// Built files have the content hash in their name, so a saved copy is always right.
async function asset(req) {
    const saved = await caches.match(req);
    if (saved) return saved;
    const res = await fetch(req);
    await put(req, res.clone());
    return res;
}

self.addEventListener("fetch", (e) => {
    const req = e.request;
    if (req.method !== "GET") return;
    const url = new URL(req.url);
    if (url.origin !== location.origin || /^\/(api|auth|health)\b/.test(url.pathname)) return;
    if (req.mode === "navigate") e.respondWith(page(e));
    else if (url.pathname.startsWith("/assets/")) e.respondWith(asset(req));
});
