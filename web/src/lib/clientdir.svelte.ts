/**
 * Clients kept on the phone, so searching works at once and with no signal at all.
 * The list comes from /api/client-directory (names, phone, age; no medical notes) and is
 * refreshed in the background; clients made on this phone are added straight away.
 */
import { api, ok, type S } from "../api/client";
import { RECONNECTED } from "./net.svelte";
import { heldClients, SENT, tokenUser } from "./outbox.svelte";

type C = S["ClientResponse"];
type Saved = { at: number; list: C[] };

const REFRESH_MS = 10 * 60_000;
const key = () => `clientdir:${tokenUser()}`;

function read(): Saved {
    try {
        const v = JSON.parse(localStorage.getItem(key()) ?? "null") as Saved | null;
        return v && Array.isArray(v.list) ? v : { at: 0, list: [] };
    } catch {
        return { at: 0, list: [] };
    }
}

let saved: Saved | null = null;
const current = () => (saved ??= read());

function write(v: Saved) {
    saved = v;
    try {
        localStorage.setItem(key(), JSON.stringify(v));
    } catch {
        /* phone storage full: keep it in memory */
    }
}

let busy = false;
/** Fetch the list again if it's old (or always, with force). Quiet on failure. */
export async function refreshDirectory(force = false) {
    if (busy || !tokenUser() || !navigator.onLine) return;
    if (!force && Date.now() - current().at < REFRESH_MS) return;
    busy = true;
    try {
        write({ at: Date.now(), list: await ok(api.GET("/api/client-directory")) });
    } catch {
        /* no access or no signal: search falls back to the server */
    } finally {
        busy = false;
    }
}

/** Add or update one client (just made, or just edited). */
export function rememberClient(c: C) {
    const v = current();
    write({ at: v.at, list: [c, ...v.list.filter((x) => x.id !== c.id)] });
}

/** A client by id, if the phone has it (made here or in the saved list). */
export function findLocal(id: string): C | undefined {
    return heldClients().find((c) => c.id === id) ?? current().list.find((c) => c.id === id);
}

/** Forget what was loaded for the last person (sign-out / sign-in). */
export function resetDirectory() {
    saved = null;
}

const fold = (t: string) => t.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");

/** Same idea as the server: every word must be in a name, or start the phone number. */
export function searchLocal(term: string, limit = 20): C[] {
    const words = fold(term).split(/\s+/).filter(Boolean);
    if (!words.length) return [];
    const all = [...heldClients(), ...current().list];
    const seen = new Set<string>();
    const hits: { c: C; score: number }[] = [];
    for (const c of all) {
        if (seen.has(c.id)) continue;
        seen.add(c.id);
        const names = [c.firstName, c.middleName, c.lastName, c.suffix].filter(Boolean).map((n) => fold(n!));
        const phone = (c.phoneNumber ?? "").replace(/\s/g, "");
        if (!words.every((w) => names.some((n) => n.includes(w)) || phone.startsWith(w))) continue;
        hits.push({ c, score: words.filter((w) => names.some((n) => n.startsWith(w))).length });
    }
    hits.sort((a, b) => b.score - a.score || fold(a.c.lastName ?? "").localeCompare(fold(b.c.lastName ?? "")));
    return hits.slice(0, limit).map((h) => h.c);
}

window.addEventListener(RECONNECTED, () => void refreshDirectory());
window.addEventListener(SENT, () => void refreshDirectory(true));
