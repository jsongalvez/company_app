/** Undoes what a starter set up. Safe to call more than once. */
export type Stop = () => void;

/**
 * The listeners and timers one starter adds, kept so its stop handle removes every one of them.
 * Nothing in web/src/lib listens or starts a timer at import time (tests/unit/wiring.test.ts):
 * a starter called from main.ts does it, through one of these, and returns `stop`.
 */
export function wiring() {
    let undo: Stop[] = [];
    return {
        on<E extends Event = Event>(target: EventTarget, type: string, fn: (e: E) => void, opts?: boolean | AddEventListenerOptions) {
            const f = fn as EventListener;
            target.addEventListener(type, f, opts);
            undo.push(() => target.removeEventListener(type, f, opts));
        },
        every(ms: number, fn: () => void) {
            const t = setInterval(fn, ms);
            undo.push(() => clearInterval(t));
        },
        after(ms: number, fn: () => void) {
            const t = setTimeout(fn, ms);
            undo.push(() => clearTimeout(t));
        },
        /** Another starter's stop, or any undo step, run when this one stops. */
        also(stop: Stop) {
            undo.push(stop);
        },
        stop: (() => {
            const steps = undo.reverse();
            undo = [];
            for (const s of steps) s();
        }) as Stop,
    };
}
