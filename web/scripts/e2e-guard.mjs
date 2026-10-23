// Decides whether `npm run e2e` may start. Rule: the full browser suite runs on GitHub only.
// Locally, name the test files you touched. GitHub sets CI=true, so its workflow passes straight through.

const TAKES_VALUE = new Set([
    "--config", "--target", "--tag", "--tag-mode", "--exclude-tag", "--grep", "--grep-invert",
    "--shard", "--agent", "--output", "--workers", "--retries", "--timeout",
]);
const NARROWS = new Set(["--grep", "--tag", "--last-failed"]);

export function refusal(args, env) {
    if (env.CI) return null;
    let narrowed = false;
    for (let i = 0; i < args.length; i++) {
        const a = args[i];
        const name = a.split("=")[0];
        if (NARROWS.has(name)) narrowed = true;
        else if (a.startsWith("-")) {
            if (TAKES_VALUE.has(a)) i++;
        } else narrowed = true;
    }
    if (narrowed) return null;
    return [
        "The full browser suite runs on GitHub only (rule: run only the touched browser tests locally).",
        "Run the file you touched:  cd web && npm run e2e -- tests/<name>.e2e.ts",
        "Or one test by title:      cd web && npm run e2e -- --grep \"<title>\"",
        "To run everything, push and let the browser workflow do it.",
    ].join("\n");
}
