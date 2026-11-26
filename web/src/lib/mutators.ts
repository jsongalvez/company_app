/**
 * A queued save, applied on this phone to the documents it changes, so the page shows it before it lands.
 * The cache keeps the server's answer as it came; what a page shows is that answer with this person's
 * unsent saves applied on top, oldest first ([applyOps]). Nothing here is stored, so a save is never
 * applied twice: when it lands it leaves the queue, and the server's answer takes its place ([takeAnswer]).
 * A save that may already be in the answer (in flight) checks the record's version first and does nothing
 * if the record moved past the version the save was made from.
 *
 * Covered, each checked against the server's answer in tests/unit/mutators.test.ts: expense add, change,
 * delete, restore; product sale change (same product), delete, restore; stock movement; client details;
 * session price, void, restore; booking change. New sessions, clients, marks, sales and clock-ins are
 * shown by Today and the client directory themselves (outbox.svelte.ts `pendingSessions`, `pendingClients`,
 * `heldMarks`, `pendingSales`, `heldClock`).
 */

export type PendingOp = { method: string; path: string; body: Record<string, unknown>; at: string; user: string };
type Rec = Record<string, unknown>;
type Change = (r: Rec, op: PendingOp) => Rec | undefined;

type Mutator = {
    method: string;
    path: RegExp;
    /** Cache key families whose documents hold the record. */
    families: string[];
    /** The record's id, from the path. */
    id?: (op: PendingOp) => string;
    /** The changed record, or undefined to leave it as it is. */
    change?: Change;
    /** Adds a record to the document under `key`, if it is the list the record belongs in. */
    add?: (key: string, op: PendingOp) => Rec | undefined;
};

const seg = (n: number) => (op: PendingOp) => op.path.split("/")[n]!;
const money = (v: unknown) => Number(v).toFixed(2);
const s = (v: unknown) => (v === undefined ? undefined : (v as string | null));

/** A versioned save made from `expected`: applies when the record is still there, and moves it one on, as the server does. */
const fromVersion =
    (field: string, f: (r: Rec, op: PendingOp) => Rec): Change =>
    (r, op) =>
        r.version === op.body[field] ? { ...f(r, op), version: (r.version as number) + 1 } : undefined;

const deleted = fromVersion("expectedVersion", (r, op) => ({ ...r, deletedAt: op.at, deletedBy: op.user, deletedReason: s(op.body.reason) ?? null }));
/** Restoring moves no version on the server. */
const restored: Change = (r) => (r.deletedAt ? { ...r, deletedAt: null, deletedBy: null, deletedReason: null } : undefined);

const CLIENT_FIELDS = [
    "firstName",
    "lastName",
    "middleName",
    "suffix",
    "phoneNumber",
    "address",
    "gender",
    "age",
    "systolicBp",
    "diastolicBp",
    "medicalConditions",
];

const MUTATORS: Mutator[] = [
    {
        method: "POST",
        path: /^\/api\/expenses$/,
        families: ["exp"],
        add: (key, op) =>
            op.body.branchDayId && key === `exp:${String(op.body.branchDayId)}`
                ? {
                      id: op.body.id,
                      branchDayId: op.body.branchDayId,
                      amount: money(op.body.amount),
                      category: op.body.category,
                      notes: s(op.body.notes) ?? null,
                      createdBy: op.user,
                      createdAt: op.at,
                      deletedBy: null,
                      deletedAt: null,
                      version: 1,
                  }
                : undefined,
    },
    {
        method: "PATCH",
        path: /^\/api\/expenses\/[^/]+$/,
        families: ["exp"],
        id: seg(3),
        change: fromVersion("expectedVersion", (r, op) => ({
            ...r,
            amount: money(op.body.amount),
            category: op.body.category,
            notes: s(op.body.notes) ?? null,
        })),
    },
    { method: "DELETE", path: /^\/api\/expenses\/[^/]+$/, families: ["exp"], id: seg(3), change: deleted },
    { method: "POST", path: /^\/api\/expenses\/[^/]+\/restore$/, families: ["exp"], id: seg(3), change: restored },
    {
        method: "PATCH",
        path: /^\/api\/product-sales\/[^/]+$/,
        families: ["sales"],
        id: seg(3),
        // Another product needs its name and price: the server's answer brings them.
        change: (r, op) =>
            op.body.productId === r.productId
                ? fromVersion("expectedVersion", (x) => ({
                      ...x,
                      quantity: op.body.quantity,
                      totalAmountAtTime: money(Number(x.unitPriceAtTime) * Number(op.body.quantity)),
                  }))(r, op)
                : undefined,
    },
    { method: "DELETE", path: /^\/api\/product-sales\/[^/]+$/, families: ["sales"], id: seg(3), change: deleted },
    { method: "POST", path: /^\/api\/product-sales\/[^/]+\/restore$/, families: ["sales"], id: seg(3), change: restored },
    {
        method: "POST",
        path: /^\/api\/branches\/[^/]+\/inventory\/[^/]+\/movement$/,
        families: ["inv"],
        id: seg(5),
        // Matched by product: the row's own id is not in the path.
        change: fromVersion("expectedVersion", (r, op) => {
            const n = Number(op.body.quantityChange);
            const out: Rec = { ...r, currentStock: Number(r.currentStock) + n };
            if (typeof r.available === "number") out.available = r.available + n;
            if (op.body.reason === "ADJUSTMENT" && typeof r.adjustment === "number") out.adjustment = r.adjustment + n;
            return out;
        }),
    },
    {
        method: "PATCH",
        path: /^\/api\/clients\/[^/]+$/,
        families: ["client", "clients"],
        id: seg(3),
        change: (r, op) => {
            const out = { ...r };
            for (const f of CLIENT_FIELDS) if (op.body[f] !== undefined && op.body[f] !== null) out[f] = op.body[f];
            for (const f of (op.body.clearFields as string[] | undefined) ?? []) if (CLIENT_FIELDS.includes(f)) out[f] = null;
            return out;
        },
    },
    {
        method: "PATCH",
        path: /^\/api\/sessions\/[^/]+\/final-price$/,
        families: ["session", "dsess", "dash"],
        id: seg(3),
        change: fromVersion("version", (r, op) => ({ ...r, finalPrice: money(op.body.finalPrice) })),
    },
    {
        method: "POST",
        path: /^\/api\/sessions\/[^/]+\/void$/,
        families: ["session", "dsess", "dash"],
        id: seg(3),
        change: (r) => (r.isVoided ? undefined : { ...r, isVoided: true }),
    },
    {
        method: "POST",
        path: /^\/api\/sessions\/[^/]+\/unvoid$/,
        families: ["session", "dsess", "dash"],
        id: seg(3),
        change: (r) => (r.isVoided ? { ...r, isVoided: false } : undefined),
    },
    {
        method: "PATCH",
        path: /^\/api\/branches\/[^/]+\/bookings\/[^/]+$/,
        families: ["bookings"],
        id: seg(5),
        change: (r, op) => ({
            ...r,
            visitDate: op.body.visitDate,
            visitTime: s(op.body.visitTime) ?? null,
            note: s(op.body.note) ?? null,
            status: op.body.status,
            updatedAt: op.at,
        }),
    },
];

const family = (key: string) => key.split(":")[0]!;
const mutatorOf = (op: PendingOp) => MUTATORS.find((m) => m.method === op.method && m.path.test(op.path));

/** Is a record the one this op is about? Stock rows are matched by product. */
const isTarget = (m: Mutator, r: Rec, op: PendingOp) => (m.families[0] === "inv" ? r.productId : r.id) === m.id!(op);

/** Today's booked list shows only visits still booked. */
const dropsOut = (key: string, r: Rec) => family(key) === "bookings" && !key.startsWith("bookings:client:") && r.status !== "BOOKED";

/** Apply `f` to each record in a document (a list, one record, or a document with a `sessions` list); same document back if none changed. */
function eachRecord(key: string, doc: unknown, f: (r: Rec) => Rec | undefined): unknown {
    if (Array.isArray(doc)) {
        let changed = false;
        const out: unknown[] = [];
        for (const r of doc as Rec[]) {
            const n = r && typeof r === "object" ? f(r) : undefined;
            if (n === undefined) {
                out.push(r);
                continue;
            }
            changed = true;
            if (!dropsOut(key, n)) out.push(n);
        }
        return changed ? out : doc;
    }
    if (!doc || typeof doc !== "object") return doc;
    const d = doc as Rec;
    if (Array.isArray(d.sessions)) {
        const sessions = eachRecord(key, d.sessions, f);
        return sessions === d.sessions ? doc : { ...d, sessions };
    }
    return f(d) ?? doc;
}

/** One op on one document. */
export function applyOp(key: string, doc: unknown, op: PendingOp): unknown {
    const m = mutatorOf(op);
    if (!m || doc === undefined || doc === null || !m.families.includes(family(key))) return doc;
    if (m.add) {
        const r = m.add(key, op);
        if (!r || !Array.isArray(doc) || (doc as Rec[]).some((x) => x.id === r.id)) return doc;
        return [...doc, r];
    }
    return eachRecord(key, doc, (r) => (isTarget(m, r, op) ? m.change!(r, op) : undefined));
}

/** A document as this person sees it: the server's answer with their unsent saves applied, oldest first. */
export function applyOps(key: string, doc: unknown, ops: PendingOp[]): unknown {
    let out = doc;
    for (const op of ops) out = applyOp(key, out, op);
    return out;
}

/** Does any mutator change documents under this key? Lets the cache skip the rest cheaply. */
export const mutates = (key: string) => MUTATORS.some((m) => m.families.includes(family(key)));

/**
 * A save landed and the server answered with the record: put it in place of the record in a document,
 * so the page keeps showing the change between the save leaving the queue and the next read.
 * An answer older than the record shown is ignored.
 */
export function takeAnswer(key: string, doc: unknown, op: PendingOp, answer: unknown): unknown {
    const m = mutatorOf(op);
    if (!m || !answer || typeof answer !== "object" || !m.families.includes(family(key))) return doc;
    const a = answer as Rec;
    if (m.add) {
        // A create: the answer is the new row, in place of the one shown here, or added if the page never showed it.
        if (!m.add(key, op) || !Array.isArray(doc) || a.id !== op.body.id) return doc;
        const rows = doc as Rec[];
        return rows.some((r) => r.id === a.id) ? rows.map((r) => (r.id === a.id ? a : r)) : [...rows, a];
    }
    // Only answers that are the record itself (a void's answer is the void, not the session).
    if ((m.families[0] === "inv" ? a.productId : a.id) !== m.id!(op) || (m.families[0] === "inv" && a.currentStock === undefined)) return doc;
    return eachRecord(key, doc, (r) => {
        if (!isTarget(m, r, op)) return undefined;
        if (typeof r.version === "number" && typeof a.version === "number" && a.version < r.version) return undefined;
        // The save's own change first (an answer leaves out fields it holds as empty), then the answer's
        // value for each field the row has (a list row may be narrower or wider than the answer).
        const out = { ...(m.change!(r, op) ?? r) };
        for (const k of Object.keys(out)) if (k in a) out[k] = a[k];
        return JSON.stringify(out) === JSON.stringify(r) ? (dropsOut(key, r) ? r : undefined) : out;
    });
}
