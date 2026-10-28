/** This build's main script name (index-<content hash>.js). It changes only when the app's code does, so it names the version in feedback, telemetry and error reports, and the update check compares it with the server's. */
export const BUILD = /\/assets\/index-[\w-]+\.js/;
export const APP_VERSION = BUILD.exec([...document.scripts].map((s) => s.src).join(" "))?.[0].slice(8, -3) ?? "dev";
