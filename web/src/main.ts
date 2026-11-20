import "./app.css";
import { mount } from "svelte";
import App from "./App.svelte";
import { startBackgroundSync } from "./lib/sync";
import { installRecorder, recorder } from "./lib/record";
import { diskKind, keepOnPhone } from "./lib/disk";
import { getToken } from "./api/client";
import { loadShell } from "./lib/shell";
import { reveal, saveSnapshotOnLeave } from "./lib/snapshot";

// The phone's saved data loads first (lib/disk.ts awaits IndexedDB), so the page may have finished
// loading before this line runs: a step meant for after load then runs at once.
const afterLoad = (f: () => void) => (document.readyState === "complete" ? f() : addEventListener("load", f));

installRecorder();
startBackgroundSync();
// Signed in: fetch the app alongside the start request. Signed out: after the sign-in screen is up.
// A failed load reloads or shows Reload (lib/shell.ts, App.svelte), so it is not an error here.
const prefetch = () => void loadShell().catch(() => {});
if (getToken()) prefetch();
else afterLoad(prefetch);
mount(App, { target: document.getElementById("app")! });
// A day's work lives on the phone: ask the browser not to clear it, and record where it is kept.
void keepOnPhone().then((kept) => recorder.record("net", { storage: diskKind, kept }));
reveal();
saveSnapshotOnLeave();

// Keep the app on the phone for weak signal (production builds only).
if (import.meta.env.PROD && "serviceWorker" in navigator) {
    afterLoad(() => void navigator.serviceWorker.register("/sw.js").catch(() => {}));
}
