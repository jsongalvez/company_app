# Rules for agents

One row per rule. "Enforced by" is the file that fails when the rule breaks, or `judgment` when nothing does. Word list: [CONTEXT.md](CONTEXT.md).

| Rule                                                                                                                                                    | Enforced by                                                               |
| ------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| No real data. Move fast, breaking changes are fine                                                                                                      | judgment                                                                  |
| Work on master. Several workers on one repo use a PR each, the lead merges                                                                              | judgment                                                                  |
| Check the GitHub runs after every push and fix red before new work                                                                                      | judgment (on the VPS `app-shipcheck [SHA]`: runs, live image, /health)    |
| Run the full browser suite on GitHub only. Locally run only the touched files                                                                           | `web/scripts/e2e-guard.mjs`, tested by `web/tests/unit/e2e-guard.test.ts` |
| Push with `tools/push.sh`, not bare `git push`: it runs ktlint, detekt, `-Werror` compiles and the dead-code gate first                                 | `tools/push.sh`; CI `quality.yml` runs the same gates                     |
| Workflows and tool scripts name only paths, modules and Gradle tasks that exist                                                                         | `tools/quality/check-references.sh`; `tools/push.sh`                      |
| Web passes svelte-check (warnings fail) and prettier                                                                                                    | `.github/workflows/web.yml`                                               |
| Screens use the word list, with no explainer sentences or code names                                                                                    | `web/tests/unit/copy.test.ts`                                             |
| Every API endpoint survives random input without a 500                                                                                                  | `.github/workflows/fuzz.yml`, `tools/fuzz/run.sh`                         |
| An offline save that replays is idempotent and carries a reason after midnight                                                                          | `web/tests/unit/replay.test.ts`, `tools/tla/outbox`                       |
| Shell tools parse and keep their self-tests green                                                                                                       | `tools/quality/*test*.sh`, run by `quality.yml`                           |
| Ask one question before building a rule that blocks people                                                                                              | judgment                                                                  |
| Reproduce first and show the failing output, then the passing output                                                                                    | judgment                                                                  |
| Say whether each claim is checked, inferred or guessed                                                                                                  | judgment                                                                  |
| After two failed fixes on one belief, test the belief before a third                                                                                    | judgment                                                                  |
| Start big features early in the usage window                                                                                                            | judgment                                                                  |
| Related feedback reports go to one worker, so a shared cause is fixed once                                                                              | judgment                                                                  |
| Log first, fix later: recurring mistakes go in [docs/patterns.md](docs/patterns.md); a rule, type or lint is built only after the user approves the row | judgment                                                                  |
| A PR is checked in the real app (`.claude/skills/verify`) on the pages it touches before it merges                                                      | judgment                                                                  |
| A step that needs no judgment becomes a script, not a paragraph                                                                                         | judgment                                                                  |
| Deploys go through the deploy gate after CI is green, not by hand                                                                                       | judgment (gate lives on the VPS, outside this repo)                       |

When a rule says `judgment` and keeps getting broken, build the check and move its row.
