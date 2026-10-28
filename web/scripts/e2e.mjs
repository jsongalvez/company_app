// `npm run e2e`: refuses a full local browser run, points a local run at the verify copy,
// otherwise hands the arguments to `e2e run`.
import { spawnSync } from "node:child_process";
import { localTarget, refusal } from "./e2e-guard.mjs";

const args = process.argv.slice(2);
const refused = refusal(args, process.env);
if (refused) {
    console.error(refused);
    process.exit(2);
}
const env = { ...process.env };
const target = localTarget(env);
if (target) {
    const up = await fetch(`${target}/health`, { signal: AbortSignal.timeout(3000) }).then(
        (r) => r.ok,
        () => false,
    );
    if (!up) {
        console.error(`Nothing answers at ${target}. Start the local copy first:  tools/verify/verify.sh up`);
        console.error("Or point at another running app with APP_URL=<url>.");
        process.exit(2);
    }
    env.APP_URL = target;
}
const r = spawnSync("npx", ["e2e", "run", ...args], { stdio: "inherit", env });
process.exit(r.status ?? 1);
