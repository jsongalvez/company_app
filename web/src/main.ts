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
if (getToken()) void loadShell();
else addEventListener("load", () => void loadShell());
mount(App, { target: document.getElementById("app")! });

// Keep the app on the phone for weak signal (production builds only).
if (import.meta.env.PROD && "serviceWorker" in navigator) {
    addEventListener("load", () => navigator.serviceWorker.register("/sw.js").catch(() => {}));
}
