// The signed-in app ships as its own file, so the sign-in screen paints without downloading it; each
// screen but Today is a file of its own too (lib/pages.svelte.ts). After a deploy the server has only the
// new build's files, so a page from the old build can't fetch them: reload once to get the new build.
// If that fails too, App offers a Reload button.
const RELOADED_AT = "shell-reloaded-at";
const RELOAD_GAP_MS = 60_000;

export function importOrReload<T>(load: () => Promise<T>): Promise<T> {
    return load().catch((e: unknown) => {
        if (Date.now() - Number(sessionStorage.getItem(RELOADED_AT) ?? 0) > RELOAD_GAP_MS) {
            sessionStorage.setItem(RELOADED_AT, String(Date.now()));
            location.reload();
        }
        throw e;
    });
}

let shell: Promise<typeof import("../components/Shell.svelte")> | undefined;

export const loadShell = () =>
    (shell ??= importOrReload(() => import("../components/Shell.svelte")).catch((e: unknown) => {
        shell = undefined;
        throw e;
    }));
