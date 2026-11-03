// Server time of the read routes behind the main screens, from the app's own Server-Timing header (db + app),
// so the network and the client are out of the number. Signs in once, warms each route, then sends --n requests
// per route one after another and prints the median, p90 and min of db, app and their total.
// For an A/B on one machine, run it against a copy of the old commit too: `tools/verify/verify.sh at <sha>` serves
// one on port 18282. Run both copies on the same load; this VPS's other processes move single numbers by 2-3x.
//   node tools/perf/route-bench.mjs --base http://127.0.0.1:18181 --user owner --pass owner-dev-pass [--n 40]
const arg = (name, dflt) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : dflt;
};
const BASE = arg("base", "http://127.0.0.1:18181");
const USER = arg("user", process.env.CALLS_USER ?? "owner");
const PASS = arg("pass", process.env.CALLS_PASS ?? "owner-dev-pass");
const N = Number(arg("n", "40"));

const login = await fetch(`${BASE}/auth/login`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ username: USER, password: PASS }),
});
if (!login.ok) throw new Error(`login ${login.status}`);
const { token } = await login.json();
const auth = { Authorization: `Bearer ${token}` };
const start = await (
    await fetch(`${BASE}/api/start`, { headers: auth })
).json();
const b = start.branchId;
if (!b) throw new Error("no branchId in /api/start");
const day =
    start.shift?.date ??
    new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Manila" }).format(
        new Date(Date.now() - 4 * 3600_000),
    );
const dayId = start.today?.branchDayId;

const routes = [
    ["GET /api/start", "/api/start"],
    ["GET today-screen", `/api/branches/${b}/today-screen`],
    ["GET daily-summary", `/api/branches/${b}/daily-summary?date=${day}`],
    ["GET sessions", `/api/branches/${b}/sessions?date=${day}`],
    ["GET product-sales", `/api/product-sales?branchDayId=${dayId}`],
    ["GET members", `/api/branches/${b}/members`],
];

const timing = (h) => {
    const t = Object.fromEntries(
        [...(h.get("server-timing") ?? "").matchAll(/(\w+);dur=([\d.]+)/g)].map(
            (m) => [m[1], Number(m[2])],
        ),
    );
    return { db: t.db ?? NaN, app: t.app ?? NaN };
};
const q = (xs, p) =>
    [...xs].sort((x, y) => x - y)[
        Math.min(xs.length - 1, Math.floor(p * xs.length))
    ];

console.log(`route-bench ${BASE} n=${N}  (ms: median / p90 / min)`);
console.log(
    `${"route".padEnd(20)} ${"status".padEnd(6)} ${"total".padEnd(16)} ${"db".padEnd(16)} app`,
);
for (const [name, path] of routes) {
    const rows = [];
    let status = 0;
    for (let i = 0; i < N + 5; i++) {
        const r = await fetch(`${BASE}${path}`, { headers: auth });
        await r.arrayBuffer();
        status = r.status;
        if (i >= 5) rows.push(timing(r.headers));
    }
    const fmt = (xs) =>
        `${q(xs, 0.5)} / ${q(xs, 0.9)} / ${Math.min(...xs)}`.padEnd(16);
    const total = rows.map((r) => r.db + r.app);
    console.log(
        `${name.padEnd(20)} ${String(status).padEnd(6)} ${fmt(total)} ${fmt(rows.map((r) => r.db))} ${fmt(rows.map((r) => r.app))}`,
    );
}
