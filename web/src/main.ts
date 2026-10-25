import "./app.css";
import { mount } from "svelte";
import App from "./App.svelte";
import { startBackgroundSync } from "./lib/sync";
import { installTracker } from "./lib/track";

installTracker();
startBackgroundSync();
mount(App, { target: document.getElementById("app")! });

// Keep the app on the phone for weak signal (production builds only).
if (import.meta.env.PROD && "serviceWorker" in navigator) {
    addEventListener("load", () => navigator.serviceWorker.register("/sw.js").catch(() => {}));
}
