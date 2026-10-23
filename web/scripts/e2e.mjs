// `npm run e2e`: refuses a full local browser run, otherwise hands the arguments to `e2e run`.
import { spawnSync } from "node:child_process";
import { refusal } from "./e2e-guard.mjs";

const args = process.argv.slice(2);
const refused = refusal(args, process.env);
if (refused) {
    console.error(refused);
    process.exit(2);
}
const r = spawnSync("npx", ["e2e", "run", ...args], { stdio: "inherit" });
process.exit(r.status ?? 1);
