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
import { peek } from "./cache.svelte";
import { findLocal } from "./clientdir.svelte";
import { disk } from "./disk";
import { clientName, operationalDate } from "./format";
import { key } from "./keys";
import { net } from "./net.svelte";
import { drainSiteOps, LANDED, outbox, pendingClients, SENT, SITE_OP, tokenUser, waiting, type SiteOp } from "./outbox.svelte";
import { recorder } from "./record";
import { today } from "./today.svelte";
import { ticketKey, type Member, type Ticket } from "./site-ticket";
import { buildSiteView, entryFromOp, isEntryUnsent, type ServerSessionLike, type SiteEntry, type SiteKind } from "./site-view";
import type { S } from "../api/client";
import { wiring, type Stop } from "./wiring";

export const HUB = "https://hub.kiel.party";
/** Direct hotspot IP over HTTP: Chrome skips DNS and exempts local-IP fetches from mixed content. */
export const HUB_DIRECT = "http://192.168.137.1:8787";
/** Fired when another device on the hub sent new records or cleared unsent saves. */
export const SITE_CHANGED = "app:site-changed";
/** A hub not heard from for this long counts as gone. */
const GONE_MS = 15_000;
const FAST_MS = 3_000;
/** With signal and nothing waiting the phone looks for a hub only now and then. */
const SLOW_MS = 60_000;

/** What the relay holds per save: only the op id and device stand outside the seal. */
type Sealed = { opId: string; device: string; occurredAt: string; ciphertext: string };
export type Kind = SiteKind;
/** One save as the other phones read it. */
export type Entry = SiteEntry;
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
    /** The laptop's battery, when it has one. */
    battery: null as { percent: number; charging: boolean } | null,
    /**
     * Chrome asks once before a page reaches a device on the local network. "prompt": the phone
     * waits for a tap on Find site hub, so the question comes when the person expects it.
     */
    localNetwork: "none" as "none" | "prompt" | "granted" | "denied",
    /** No signal and a ticket for today: the moment a site hub may be near. */
    offSignal: false,
});

/** Low enough that the Site panel warns: the laptop has an hour or so left. */
export const BATTERY_LOW = 15;

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

function clientOf(id: string): string | undefined {
    const c = findLocal(id) ?? pendingClients().find((p) => p.id === id);
    return c ? clientName(c) : undefined;
}

function sessionClientOf(sessionId: string, t: Ticket): string | undefined {
    for (const e of opened.values()) if (e.kind === "session" && e.sessionId === sessionId && e.client) return e.client;
    const dash = peek<S["DashboardResponse"]>(key.dashboard(t.branchId, t.date));
    return dash?.sessions.find((s) => s.id === sessionId)?.clientName ?? undefined;
}

/** This phone's save, as the others will read it; undefined for one made for another branch or day. */
function entryOf(i: SiteOp & { failed?: string }, t: Ticket): Omit<Entry, "opId"> | undefined {
    return entryFromOp(i, {
        branchId: t.branchId,
        date: t.date,
        branchDayId: today.day?.branchDayId,
        displayName: app.me?.displayName ?? "",
        clientNameById: clientOf,
        sessionClientById: (sid) => sessionClientOf(sid, t),
    });
}

/** Op ids this hub boot already holds from this phone. */
let given = { boot: "", ops: new Set<string>() };
const opened = new Map<string, Entry>();
/** Buffered direct/recent saves so fast-flushed or live saves reach the room even between ticks. */
const pendingOps: SiteOp[] = [];

type LocalFetchInit = RequestInit & { targetAddressSpace?: "local" };
let activeHub = HUB;
let lastReported = "";
const viaName = (base: string) => (base === HUB ? "https" : "direct");
const errLabel = (e: unknown) => (e instanceof Error ? `${e.name}: ${e.message}`.slice(0, 80) : String(e).slice(0, 80));

function noteHub(keyTag: string, data: Record<string, unknown>) {
    if (lastReported === keyTag) return;
    lastReported = keyTag;
    recorder.record("net", data);
}

async function postSync(base: string, room: string, body: string): Promise<Response> {
    const init: LocalFetchInit = {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
        cache: "no-store",
        signal: AbortSignal.timeout(5_000),
    };
    if (base === HUB_DIRECT) init.targetAddressSpace = "local";
    const res = await fetch(`${base}/v1/rooms/${room}/sync`, init);
    if (!res.ok) throw new Error(`hub ${res.status}`);
    return res;
}

async function tick(t: Ticket): Promise<boolean> {
    const keyObj = await cryptoKey(t);
    const me = device();
    const room = loadRoom(t.room);
    const have = new Set(room.records.map((r) => r.opId));
    for (const op of drainSiteOps()) pendingOps.push(op);
    const sources: (SiteOp & { failed?: string })[] = [...outbox.items, ...pendingOps];
    pendingOps.length = 0;
    for (const i of sources) {
        if (have.has(i.id)) continue;
        const e = entryOf(i, t);
        if (!e) continue;
        room.records.push({ opId: i.id, device: me, occurredAt: i.at, ciphertext: await seal(keyObj, i.id, { ...e, opId: i.id }) });
        have.add(i.id);
    }
    if (given.boot !== room.boot) given = { boot: room.boot, ops: new Set() };
    const presence: Presence = { user: tokenUser(), name: app.me?.displayName ?? "", unsent: waiting().map((i) => i.id) };
    const body = JSON.stringify({
        device: me,
        boot: room.boot,
        since: room.since,
        presence: await seal(keyObj, me, presence),
        records: room.records.filter((r) => !given.ops.has(r.opId)),
    });
    const t0 = performance.now();
    let res: Response;
    try {
        res = await postSync(activeHub, t.room, body);
    } catch (err) {
        const other = activeHub === HUB ? HUB_DIRECT : HUB;
        if (other === HUB_DIRECT && site.localNetwork === "denied") throw err;
        recorder.record("net", { hub: "fallback", from: viaName(activeHub), to: viaName(other), err: errLabel(err) });
        res = await postSync(other, t.room, body);
        activeHub = other;
    }
    const ms = Math.round(performance.now() - t0);
    const out = (await res.json()) as {
        boot: string;
        next: number;
        records: Sealed[];
        phones: { device: string; agoSec: number; presence: string }[];
        battery?: { percent: number; charging: boolean };
    };
    site.battery = out.battery ?? null;
    // A new boot means the hub lost what it held: send it all again at once.
    const restarted = out.boot !== room.boot;
    if (!restarted) for (const r of room.records) given.ops.add(r.opId);
    let peerNew = false;
    for (const r of out.records) {
        if (!have.has(r.opId)) {
            room.records.push(r);
            have.add(r.opId);
            if (r.device !== me) peerNew = true;
        }
    }
    room.boot = out.boot;
    room.since = out.next;
    disk.set(roomKey(t.room), JSON.stringify(room));

    for (const r of room.records) {
        if (opened.has(r.opId)) continue;
        const e = await unseal<Entry>(keyObj, r.opId, r.ciphertext);
        if (e && e.opId === r.opId) opened.set(r.opId, { ...e, own: r.device === me });
    }
    const prevPeerUnsent = site.phones.filter((p) => p.device !== me).reduce((n, p) => n + p.unsent, 0);
    const phones: Phone[] = [];
    const unsent = new Set<string>();
    for (const p of out.phones) {
        const v = await unseal<Presence>(keyObj, p.device, p.presence);
        if (!v) continue;
        phones.push({ device: p.device, name: v.name, user: v.user, agoSec: p.agoSec, unsent: v.unsent.length });
        for (const id of v.unsent) unsent.add(id);
    }
    const nowPeerUnsent = phones.filter((p) => p.device !== me).reduce((n, p) => n + p.unsent, 0);
    const seen = new Set(phones.map((p) => p.user));
    site.room = t.room;
    site.entries = room.records.flatMap((r) => opened.get(r.opId) ?? []).sort((a, b) => a.at.localeCompare(b.at));
    site.phones = phones.sort((a, b) => a.name.localeCompare(b.name));
    site.notSeen = t.members.filter((m) => !seen.has(m.userId));
    site.unsent = unsent;
    site.lastAt = Date.now();
    site.connected = true;
    noteHub(`ok:${viaName(activeHub)}:${phones.length}`, { hub: "ok", via: viaName(activeHub), phones: phones.length, recs: room.records.length, ms });
    if (peerNew || nowPeerUnsent < prevPeerUnsent) window.dispatchEvent(new Event(SITE_CHANGED));
    return restarted;
}

/** Which start the running loop belongs to, 0 while stopped: a loop from an earlier start ends at its next wake. */
let run = 0;
let starts = 0;
let next: ReturnType<typeof setTimeout> | undefined;
let permission: PermissionStatus | null = null;
let probedAt = 0;
let asked = false;

/** Chrome's permission for reaching local devices; "none" where the browser has no such question. */
async function watchLocalNetwork(mine: number) {
    for (const name of ["local-network-access", "local-network"]) {
        try {
            const st = await navigator.permissions.query({ name } as unknown as PermissionDescriptor);
            if (mine !== run) return;
            const set = () => {
                site.localNetwork = st.state;
                recorder.record("net", { hub: "perm", st: st.state });
            };
            set();
            st.onchange = set;
            permission = st;
            return;
        } catch {
            // This browser doesn't know the name: try the next, else it never asks.
        }
    }
}

/** The tap on Find site hub: ask inside the user gesture so Chrome opens the local-network prompt right away. */
export function findHub() {
    asked = true;
    probedAt = 0;
    if (!run) return;
    clearTimeout(next);
    next = undefined;
    void loop(run);
}

/** Push newly sealed saves or cleared unsent counts promptly when already connected to a hub. */
function wakeSoon() {
    if (!run || !site.connected) return;
    clearTimeout(next);
    next = setTimeout(() => void loop(run), 150);
}

/** Wakes every FAST_MS; calls the hub when one answered lately or this phone has no signal or unsent saves, else once a SLOW_MS. */
async function loop(mine: number) {
    if (mine !== run) return;
    const t = ticket();
    let again = FAST_MS;
    const looking = site.connected || !net.online || net.trouble || waiting().length > 0;
    site.offSignal = !!t && (!net.online || net.trouble);
    const mayAsk = site.localNetwork !== "prompt" || asked;
    // Another branch or a new day: its own room, nothing shown until its hub answers.
    if ((t?.room ?? "") !== site.room) Object.assign(site, { room: t?.room ?? "", lastAt: 0, connected: false, entries: [], phones: [], notSeen: [] });
    if (t && app.me && mayAsk && site.localNetwork !== "denied" && (looking || Date.now() - probedAt >= SLOW_MS)) {
        probedAt = Date.now();
        try {
            if (await tick(t)) again = 0;
        } catch (err) {
            const wasConnected = site.connected;
            if (Date.now() - site.lastAt > GONE_MS) site.connected = false;
            if (wasConnected && !site.connected) noteHub("gone", { hub: "gone", via: viaName(activeHub), err: errLabel(err) });
            else if (!site.connected)
                noteHub(`fail:${viaName(activeHub)}:${errLabel(err)}`, { hub: "fail", via: viaName(activeHub), err: errLabel(err), perm: site.localNetwork });
        }
    }
    if (mine === run) next = setTimeout(() => void loop(mine), again);
}

/** Starts looking for a site hub; called once at app start. */
export function startSite(): Stop {
    if (run || typeof crypto?.subtle === "undefined") return () => {};
    const mine = (run = ++starts);
    const w = wiring();
    w.on(window, SITE_OP, wakeSoon);
    w.on(window, SENT, wakeSoon);
    w.on(window, LANDED, wakeSoon);
    w.also(() => {
        run = 0;
        clearTimeout(next);
        next = undefined;
        if (permission) permission.onchange = null;
        permission = null;
    });
    void watchLocalNetwork(mine).finally(() => void loop(mine));
    return () => mine === run && w.stop();
}

/** True when a hub entry is still waiting to reach the server. */
export function isUnsent(entry: Entry, serverById: ReadonlyMap<string, ServerSessionLike>): boolean {
    void outbox.items.length;
    const ownWaiting = new Set(waiting().map((i) => i.id));
    return isEntryUnsent(entry, ownWaiting, site.unsent, serverById);
}

/** The Site panel's lists, from every record seen today. */
export function siteView(serverSessions: readonly ServerSessionLike[] = []) {
    return buildSiteView(site.entries, serverSessions);
}
