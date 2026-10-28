// The signed-in app ships as its own file, so the sign-in screen paints without downloading it.
// After a deploy the server has only the new build's files, so a page from the old build can't fetch
// it: reload once to get the new build. If that fails too, App offers a Reload button.
let shell: Promise<typeof import("../components/Shell.svelte")> | undefined;
const RELOADED_AT = "shell-reloaded-at";
const RELOAD_GAP_MS = 60_000;

export const loadShell = () =>
    (shell ??= import("../components/Shell.svelte").catch((e: unknown) => {
        shell = undefined;
        if (Date.now() - Number(sessionStorage.getItem(RELOADED_AT) ?? 0) > RELOAD_GAP_MS) {
            sessionStorage.setItem(RELOADED_AT, String(Date.now()));
            location.reload();
        }
        throw e;
    }));
