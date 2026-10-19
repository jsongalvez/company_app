// Full screen and "install as an app".
// Full screen: Android, computers and iPads; iPhone Safari has no full screen for pages.
// Install: Chrome/Edge/Android offer a prompt we can trigger; iPhone/iPad need Share > Add to Home Screen.

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

addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    deferred = e as InstallPrompt;
    screen.canPrompt = true;
});
addEventListener("appinstalled", () => {
    deferred = null;
    screen.canPrompt = false;
    screen.installed = true;
});
standaloneQuery.addEventListener("change", () => (screen.installed = installed()));
document.addEventListener("fullscreenchange", () => (screen.full = !!document.fullscreenElement));

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
