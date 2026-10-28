// The signed-in app ships as its own file, so the sign-in screen paints without downloading it.
let shell: Promise<typeof import("../components/Shell.svelte")> | undefined;
export const loadShell = () => (shell ??= import("../components/Shell.svelte"));
