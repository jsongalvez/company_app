/**
 * The phone's own copy of the queue and the branch's records, in IndexedDB, so a day's work and
 * everything it needs survive closing the app, low memory and (once the browser grants
 * persistence) storage pressure. localStorage holds 5 MB and Safari may clear it; IndexedDB
 * holds far more.
 *
 * Reads are synchronous from a copy loaded once before the app starts (this module awaits it at
 * the top, so every importer waits too); writes go to the copy at once and to IndexedDB behind.
 * Where IndexedDB can't open (private windows on some phones, tests in node), it falls back to
 * localStorage under the same keys.
 */

const DB = "company-app";
const STORE = "kv";
/** Keys the app kept in localStorage before this store: moved over once, then removed there. */
const MOVED = (k: string) => k === "outbox" || k.startsWith("qcache:") || k.startsWith("clientdir:");

type Backend = { put(k: string, v: string): void; del(k: string): void };

const copy = new Map<string, string>();

function idb(): Promise<IDBDatabase | null> {
    if (typeof indexedDB === "undefined") return Promise.resolve(null);
    return new Promise((resolve) => {
        try {
            const req = indexedDB.open(DB, 1);
            req.onupgradeneeded = () => req.result.createObjectStore(STORE);
            req.onsuccess = () => resolve(req.result);
            req.onerror = () => resolve(null);
            req.onblocked = () => resolve(null);
        } catch {
            resolve(null);
        }
    });
}

function readAll(db: IDBDatabase): Promise<void> {
    return new Promise((resolve) => {
        const tx = db.transaction(STORE, "readonly");
        const req = tx.objectStore(STORE).openCursor();
        req.onsuccess = () => {
            const c = req.result;
            if (!c) return resolve();
            if (typeof c.value === "string") copy.set(String(c.key), c.value);
            c.continue();
        };
        req.onerror = () => resolve();
    });
}

function idbBackend(db: IDBDatabase): Backend {
    const write = (fn: (s: IDBObjectStore) => void) => {
        try {
            fn(db.transaction(STORE, "readwrite").objectStore(STORE));
        } catch {
            /* the copy in memory still holds it until the next write */
        }
    };
    return { put: (k, v) => write((s) => s.put(v, k)), del: (k) => write((s) => s.delete(k)) };
}

const localBackend: Backend = {
    put(k, v) {
        try {
            localStorage.setItem(k, v);
        } catch {
            /* storage full: memory only */
        }
    },
    del: (k) => localStorage.removeItem(k),
};

function localKeys(): string[] {
    if (typeof localStorage === "undefined") return [];
    return Array.from({ length: localStorage.length }, (_, i) => localStorage.key(i)!).filter((k) => k && MOVED(k));
}

async function open(): Promise<{ backend: Backend; kind: "idb" | "local" }> {
    const db = await idb();
    if (!db) {
        for (const k of localKeys()) copy.set(k, localStorage.getItem(k) ?? "");
        return { backend: localBackend, kind: "local" };
    }
    await readAll(db);
    const backend = idbBackend(db);
    for (const k of localKeys()) {
        if (!copy.has(k)) {
            const v = localStorage.getItem(k) ?? "";
            copy.set(k, v);
            backend.put(k, v);
        }
        localStorage.removeItem(k);
    }
    return { backend, kind: "idb" };
}

const opened = await open();

/** Which store holds the phone's copy: the recorder logs it at boot, so a phone that fell back is visible. */
export const diskKind = opened.kind;

export const disk = {
    get: (k: string): string | null => copy.get(k) ?? null,
    set(k: string, v: string) {
        copy.set(k, v);
        opened.backend.put(k, v);
    },
    del(k: string) {
        copy.delete(k);
        opened.backend.del(k);
    },
    keys: (prefix: string): string[] => [...copy.keys()].filter((k) => k.startsWith(prefix)),
};

/**
 * Ask the browser to keep this site's data under storage pressure. Chrome grants it to an
 * installed or often-used app; Safari to one added to the home screen. Without it, Safari may
 * clear a site's data after seven days unused.
 */
export async function keepOnPhone(): Promise<boolean> {
    try {
        if (!navigator.storage?.persist) return false;
        if (await navigator.storage.persisted()) return true;
        return await navigator.storage.persist();
    } catch {
        return false;
    }
}
