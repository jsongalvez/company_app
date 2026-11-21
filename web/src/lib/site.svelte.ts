/**
 * The site hub: with no signal, phones on the laptop's hotspot reach a relay at HUB (repo `hub/`)
 * and mirror their day's saves there, sealed with the branch day's key, so each phone sees what the
 * others started (who is waiting, who is on duty, what was sold) and who is not connected. The
 * cloud stays the only judge: every phone still sends its own saves when the signal is back.
 *
 * Every phone keeps every record it has seen, so a hub that restarts empty is refilled by the
 * phones. With no hub answering nothing shows and nothing changes.
 */
import { app } from "./app.svelte";
import { findLocal } from "./clientdir.svelte";
import { disk } from "./disk";
import { clientName, operationalDate } from "./format";
import { net } from "./net.svelte";
import { outbox, pendingClients, tokenUser, waiting, type Item } from "./outbox.svelte";
import { parseBody } from "./outbox-classify";
import { today } from "./today.svelte";
import { ticketKey, type Member, type Ticket } from "./site-ticket";

export const HUB = "https://hub.kiel.party";
/** A hub not heard from for this long counts as gone. */
const GONE_MS = 15_000;
const FAST_MS = 3_000;
/** With signal and nothing waiting the phone looks for a hub only now and then. */
const SLOW_MS = 60_000;

/** What the relay holds per save: only the op id and device stand outside the seal. */
type Sealed = { opId: string; device: string; occurredAt: string; ciphertext: string };
export type Kind = "session" | "mark" | "clock-in" | "clock-out" | "sale" | "other";
/** One save as the other phones read it. */
export type Entry = {
    opId: string;
    kind: Kind;
    user: string;
    name: string;
    at: string;
    what: string;
    client?: string;
    sessionId?: string;
    status?: string;
    quantity?: number;
};
type Presence = { user: string; name: string; unsent: string[] };
export type Phone = { device: string; name: string; user: string; agoSec: number; unsent: number };
type Room = { boot: string; since: number; records: Sealed[] };

export const site = $state({
    /** The room shown: today's at the branch picked, when this phone holds its ticket. */
    room: "",
    /** Last time the hub answered, ms; 0 when it never has today. */
    lastAt: 0,
    connected: false,
    entries: [] as Entry[],
    phones: [] as Phone[],
    notSeen: [] as Member[],
    unsent: new Set<string>(),
});

const roomKey = (room: string) => `site:room:${room}`;

function ticket(): Ticket | undefined {
    try {
        const t = JSON.parse(disk.get(ticketKey(app.branchId)) ?? "null") as Ticket | null;
        return t && t.date === operationalDate() ? t : undefined;
    } catch {
        return undefined;
    }
}

function device(): string {
    let d = disk.get("site:device");
    if (!d) {
        d = Array.from(crypto.getRandomValues(new Uint8Array(8)), (b) => b.toString(16).padStart(2, "0")).join("");
        disk.set("site:device", d);
    }
    return d;
}

function loadRoom(room: string): Room {
    try {
        return { boot: "", since: 0, records: [], ...(JSON.parse(disk.get(roomKey(room)) ?? "{}") as Partial<Room>) };
    } catch {
        return { boot: "", since: 0, records: [] };
    }
}

const b64 = (u: Uint8Array) =>
    btoa(String.fromCharCode(...u))
        .replace(/\+/g, "-")
        .replace(/\//g, "_")
        .replace(/=+$/, "");
const unb64 = (s: string) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));
const enc = new TextEncoder();

const keys = new Map<string, Promise<CryptoKey>>();
function cryptoKey(t: Ticket): Promise<CryptoKey> {
    let k = keys.get(t.key);
    if (!k) keys.set(t.key, (k = crypto.subtle.importKey("raw", unb64(t.key), "AES-GCM", false, ["encrypt", "decrypt"])));
    return k;
}

/** Sealed with AES-GCM, bound to `bound` (the op id or the device) so a record can't be passed off as another. */
async function seal(key: CryptoKey, bound: string, value: unknown): Promise<string> {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: enc.encode(bound) }, key, enc.encode(JSON.stringify(value))));
    const out = new Uint8Array(iv.length + ct.length);
    out.set(iv);
    out.set(ct, iv.length);
    return b64(out);
}

async function unseal<T>(key: CryptoKey, bound: string, s: string): Promise<T | undefined> {
    try {
        const raw = unb64(s);
        const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: raw.slice(0, 12), additionalData: enc.encode(bound) }, key, raw.slice(12));
        return JSON.parse(new TextDecoder().decode(plain)) as T;
    } catch {
        return undefined;
    }
}

/** The branch a save is for, from its body or query; a sale names today's day there. */
function branchOf(i: Item, b: Record<string, unknown>): string | undefined {
    if (typeof b.branchId === "string") return b.branchId;
    const q = new URLSearchParams(i.query ?? "").get("branchId");
    if (q) return q;
    if (b.branchDayId && b.branchDayId === today.day?.branchDayId) return app.branchId;
    return undefined;
}

function clientOf(id: string): string | undefined {
    const c = findLocal(id) ?? pendingClients().find((p) => p.id === id);
    return c ? clientName(c) : undefined;
}

/** This phone's save, as the others will read it; undefined for one made for another branch or day. */
function entryOf(i: Item, t: Ticket): Omit<Entry, "opId"> | undefined {
    if (i.failed || operationalDate(new Date(i.at)) !== t.date) return undefined;
    const b = parseBody(i.body);
    if (branchOf(i, b) !== t.branchId) return undefined;
    const base = { user: i.user, name: app.me?.displayName ?? "", at: i.at, what: i.what };
    if (i.method === "POST" && i.path === "/api/sessions")
        return { ...base, kind: "session", sessionId: String(b.id), client: clientOf(String(b.clientId)) ?? "New client" };
    const mark = i.path.match(/^\/api\/sessions\/([^/]+)\/status$/);
    if (i.method === "PATCH" && mark) return { ...base, kind: "mark", sessionId: mark[1], status: String(b.status) };
    if (i.path === "/api/attendance/clock-in") return { ...base, kind: "clock-in" };
    if (i.path === "/api/attendance/clock-out") return { ...base, kind: "clock-out" };
    if (i.method === "POST" && i.path === "/api/product-sales") return { ...base, kind: "sale", quantity: Number(b.quantity ?? 0) };
    return { ...base, kind: "other" };
}

/** Op ids this hub boot already holds from this phone. */
let given = { boot: "", ops: new Set<string>() };
const opened = new Map<string, Entry>();

async function tick(t: Ticket): Promise<boolean> {
    const key = await cryptoKey(t);
    const me = device();
    const room = loadRoom(t.room);
    const have = new Set(room.records.map((r) => r.opId));
    for (const i of outbox.items) {
        if (have.has(i.id)) continue;
        const e = entryOf(i, t);
        if (!e) continue;
        room.records.push({ opId: i.id, device: me, occurredAt: i.at, ciphertext: await seal(key, i.id, { ...e, opId: i.id }) });
        have.add(i.id);
    }
    if (given.boot !== room.boot) given = { boot: room.boot, ops: new Set() };
    const presence: Presence = { user: tokenUser(), name: app.me?.displayName ?? "", unsent: waiting().map((i) => i.id) };
    const res = await fetch(`${HUB}/v1/rooms/${t.room}/sync`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            device: me,
            boot: room.boot,
            since: room.since,
            presence: await seal(key, me, presence),
            records: room.records.filter((r) => !given.ops.has(r.opId)),
        }),
        cache: "no-store",
        signal: AbortSignal.timeout(5_000),
    });
    if (!res.ok) throw new Error(`hub ${res.status}`);
    const out = (await res.json()) as { boot: string; next: number; records: Sealed[]; phones: { device: string; agoSec: number; presence: string }[] };
    // A new boot means the hub lost what it held: send it all again at once.
    const restarted = out.boot !== room.boot;
    if (!restarted) for (const r of room.records) given.ops.add(r.opId);
    for (const r of out.records) if (!have.has(r.opId)) (room.records.push(r), have.add(r.opId));
    room.boot = out.boot;
    room.since = out.next;
    disk.set(roomKey(t.room), JSON.stringify(room));

    for (const r of room.records) {
        if (opened.has(r.opId)) continue;
        const e = await unseal<Entry>(key, r.opId, r.ciphertext);
        if (e && e.opId === r.opId) opened.set(r.opId, e);
    }
    const phones: Phone[] = [];
    const unsent = new Set<string>();
    for (const p of out.phones) {
        const v = await unseal<Presence>(key, p.device, p.presence);
        if (!v) continue;
        phones.push({ device: p.device, name: v.name, user: v.user, agoSec: p.agoSec, unsent: v.unsent.length });
        for (const id of v.unsent) unsent.add(id);
    }
    const seen = new Set(phones.map((p) => p.user));
    site.room = t.room;
    site.entries = room.records.flatMap((r) => opened.get(r.opId) ?? []).sort((a, b) => a.at.localeCompare(b.at));
    site.phones = phones.sort((a, b) => a.name.localeCompare(b.name));
    site.notSeen = t.members.filter((m) => !seen.has(m.userId));
    site.unsent = unsent;
    site.lastAt = Date.now();
    site.connected = true;
    return restarted;
}

let running = false;
let probedAt = 0;
/** Wakes every FAST_MS; calls the hub when one answered lately or this phone has no signal or unsent saves, else once a SLOW_MS. */
async function loop() {
    const t = ticket();
    let again = FAST_MS;
    const looking = site.connected || !net.online || net.trouble || waiting().length > 0;
    // Another branch or a new day: its own room, nothing shown until its hub answers.
    if ((t?.room ?? "") !== site.room) Object.assign(site, { room: t?.room ?? "", lastAt: 0, connected: false, entries: [], phones: [], notSeen: [] });
    if (t && app.me && (looking || Date.now() - probedAt >= SLOW_MS)) {
        probedAt = Date.now();
        try {
            if (await tick(t)) again = 0;
        } catch {
            if (Date.now() - site.lastAt > GONE_MS) site.connected = false;
        }
    }
    setTimeout(loop, again);
}

/** Starts looking for a site hub; called once at app start. */
export function startSite() {
    if (running || typeof crypto?.subtle === "undefined") return;
    running = true;
    void loop();
}

/** The Site panel's lists, from every record seen today. */
export function siteView() {
    const marks = new Map<string, string>();
    const duty = new Map<string, Entry>();
    for (const e of site.entries) {
        if (e.kind === "mark" && e.sessionId) marks.set(e.sessionId, e.status ?? "");
        if (e.kind === "clock-in" || e.kind === "clock-out") duty.set(e.user, e);
    }
    const waitingNow = site.entries.filter((e) => e.kind === "session" && (marks.get(e.sessionId ?? "") ?? "PENDING") === "PENDING");
    const sales = site.entries.filter((e) => e.kind === "sale");
    return {
        waiting: waitingNow,
        onDuty: [...duty.values()].filter((e) => e.kind === "clock-in"),
        sales,
        sold: sales.reduce((n, e) => n + (e.quantity ?? 0), 0),
    };
}
