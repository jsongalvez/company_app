/**
 * Refresh without a blank frame: on leaving, save the screen's HTML; index.html shows it before the
 * app's script runs, and `reveal()` swaps the live app in once it has drawn its first real screen. After an
 * update it shows too, in the old build's styles, saved as text.
 */
const KEY = "snap";
const READY_CAP_MS = 2000;
const MAX_CHARS = 400_000;

const version = () => document.querySelector<HTMLScriptElement>("script[type=module][src]")?.src ?? "";
/** Stylesheets the screen needs besides the main one, which index.html already links (e.g. the lazy Shell's). */
const styles = () => [...document.querySelectorAll<HTMLLinkElement>("link[rel=stylesheet]")].map((l) => l.href).filter((h, i, all) => all.indexOf(h) === i);
const appEl = () => document.getElementById("app")!;
/** The styles as text, for the first open of a new build: the old build's files may be gone by then, and its class names need its own rules. */
const styleText = () =>
    [...document.styleSheets]
        .map((sheet) => {
            try {
                return [...sheet.cssRules].map((r) => r.cssText).join("");
            } catch {
                return "";
            }
        })
        .join("");

/** A screen worth showing: the signed-in app with nothing still loading. */
function drawn(el: HTMLElement) {
    return !!el.querySelector(".shell main") && !el.querySelector(".boot") && !el.innerText.includes("Loading…");
}

export function saveSnapshotOnLeave() {
    const save = () => {
        const el = appEl();
        if (document.getElementById("snap") || !drawn(el) || el.innerHTML.length > MAX_CHARS) return;
        localStorage.setItem(KEY, JSON.stringify({ v: version(), hash: location.hash, css: styles(), cssText: styleText(), html: el.innerHTML }));
    };
    addEventListener("pagehide", save);
    document.addEventListener("visibilitychange", () => document.visibilityState === "hidden" && save());
}

export function forgetSnapshot() {
    localStorage.removeItem(KEY);
}

/** Swap the saved screen for the live app once it has drawn (or something else, such as sign-in, shows). */
export function reveal() {
    const snap = document.getElementById("snap");
    const el = appEl();
    if (!snap?.firstChild) return snap?.remove();
    const start = performance.now();
    const tick = () => {
        const done = drawn(el) || (!!el.querySelector("form") && !el.querySelector(".shell")) || performance.now() - start > READY_CAP_MS;
        if (!done) return void requestAnimationFrame(tick);
        snap.remove();
        document.getElementById("snap-hide")?.remove();
        document.querySelector("style[data-snap-old]")?.remove();
    };
    requestAnimationFrame(tick);
}
