# Replicate the setup on a new box

Builds a second company_app box from a blank Ubuntu 24.04 VM, and proves any box matches the expected state.

Nothing here holds secrets. The tools list variable names only. Values live in Coolify and `~/.config/company-app-*.env` on each box.

## Build a box

1. Run `tools/replicate/wizard.sh cloud` from anywhere. It walks you through the VM, the 150G volume and Oracle's firewall.
2. On the box, clone the repo to `~/Projects/company_app` and run `tools/replicate/replicate.sh plan`, then `apply`.
3. When `apply` stops or lists USER items, run `tools/replicate/wizard.sh box`.
4. Run `apply` again, then `tools/replicate/replicate.sh check --strict`.
5. To copy data, stop the app in Coolify and run `tools/replicate/restore-latest.sh`.

## Commands

| Command | Effect |
| --- | --- |
| `replicate.sh check` | Read-only. One PASS, WARN or FAIL per item. Exit 1 on FAIL. |
| `replicate.sh check --strict` | WARN fails too. Use on a finished new box. |
| `replicate.sh plan` | Read-only. Shows what `apply` would do. |
| `replicate.sh apply` | Fixes failing agent items and rechecks each. Refuses the prod VPS. |
| `restore-latest.sh [NAME]` | Replaces this box's database with a backup. Refuses the prod VPS. |

Each item in `replicate.sh` is a `chk_<name>` and, for agent items, a `fix_<name>`. To change the expected state, change the function and rerun `check` on prod.

`files/` holds what a box needs and the repo had nowhere else: `files/home` maps to `$HOME`, `files/root` to `/`. `@DB_UUID@`, `@DB_PREFIX@` and `@APP_PREFIX@` are filled with the box's Coolify ids. The perf scripts come from `tools/perf`.

## Known differences

Prod warns on `ports_lock`. Coolify's ports listen on all addresses there and Docker skips ufw, so only Oracle's firewall closes them. New boxes also get `coolify-ports-lock.service`, which drops those ports on the public interface.

opt-chat, rift and the local dev databases are left out. They are not part of running the app.

Status: `check` runs on prod. `apply`, both wizard parts and `restore-latest.sh` have never run on a real new box. Expect fixes on the first run.
