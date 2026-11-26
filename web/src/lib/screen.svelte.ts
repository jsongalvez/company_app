// Full screen and "install as an app".
// Full screen: Android, computers and iPads; iPhone Safari has no full screen for pages.
// Install: Chrome/Edge/Android offer a prompt we can trigger; iPhone/iPad need Share > Add to Home Screen.

import { wiring, type Stop } from "./wiring";

type InstallPrompt = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: string }> };

const ua = navigator.userAgent;
const isIos = /iPhone|iPad|iPod/.test(ua) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
const standaloneQuery = matchMedia("(display-mode: standalone), (display-mode: fullscreen)");

function installed() {
    return standaloneQuery.matches || (navigator as Navigator & { standalone?: boolean }).standalone === true;
}

export const screen = $state({
    /** The browser lets this page go full screen. */
    canFull: !!document.documentElement.requestFullscreen && document.fullscreenEnabled !== false,
    full: !!document.fullscreenElement,
    installed: installed(),
    /** Chrome/Edge/Android: we can show the install prompt ourselves. */
    canPrompt: false,
    /** iPhone/iPad Safari: install is by hand, from the Share button. */
    iosHint: isIos && !installed(),
});

let deferred: InstallPrompt | null = null;

/** Follow install and full-screen changes; called from main.ts. */
export function startScreen(): Stop {
    const w = wiring();
    w.on(window, "beforeinstallprompt", (e) => {
        e.preventDefault();
        deferred = e as InstallPrompt;
        screen.canPrompt = true;
    });
    w.on(window, "appinstalled", () => {
        deferred = null;
        screen.canPrompt = false;
        screen.installed = true;
    });
    w.on(standaloneQuery, "change", () => (screen.installed = installed()));
    w.on(document, "fullscreenchange", () => (screen.full = !!document.fullscreenElement));
    return w.stop;
}

export async function toggleFull() {
    try {
        if (document.fullscreenElement) await document.exitFullscreen();
        else await document.documentElement.requestFullscreen({ navigationUI: "hide" });
    } catch {
        // Refused (e.g. not from a tap): nothing to undo.
    }
}

export async function install() {
    if (!deferred) return;
    await deferred.prompt();
    await deferred.userChoice.catch(() => null);
    deferred = null;
    screen.canPrompt = false;
}
