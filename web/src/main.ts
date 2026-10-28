import "./app.css";
import { mount } from "svelte";
import App from "./App.svelte";
import { startBackgroundSync } from "./lib/sync";
import { installTracker } from "./lib/track";
import { installRecorder } from "./lib/record";
import { getToken } from "./api/client";
import { loadShell } from "./lib/shell";

installTracker();
installRecorder();
startBackgroundSync();
// Signed in: fetch the app alongside the start request. Signed out: after the sign-in screen is up.
// A failed load reloads or shows Reload (lib/shell.ts, App.svelte), so it is not an error here.
const prefetch = () => void loadShell().catch(() => {});
if (getToken()) prefetch();
else addEventListener("load", prefetch);
mount(App, { target: document.getElementById("app")! });

// Keep the app on the phone for weak signal (production builds only).
if (import.meta.env.PROD && "serviceWorker" in navigator) {
    addEventListener("load", () => navigator.serviceWorker.register("/sw.js").catch(() => {}));
}
