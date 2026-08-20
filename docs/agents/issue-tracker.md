# Issue tracker: GitHub

Issues and PRDs for this repo live as GitHub issues. Use the `gh` CLI for all operations.

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`. Use a heredoc for multi-line bodies.
- **Read an issue**: `gh issue view <number> --comments`, filtering comments by `jq` and also fetching labels.
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` with appropriate `--label` and `--state` filters.
- **Comment on an issue**: `gh issue comment <number> --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --comment "..."`

### Commit references

Future non-merge commits must include `ref #<number>` somewhere in the commit
message. The local `.githooks/commit-msg` hook checks this offline, so references
may target closed issues and multiple issue references are allowed. Git-generated
merge commits are exempt. Install enforcement with `bash scripts/setup-hooks.sh`.

Infer the repo from `git remote -v` — `gh` does this automatically when run inside a clone.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

When set to `yes`, PRs run through the same labels and states as issues, using the `gh pr` equivalents:

- **Read a PR**: `gh pr view <number> --comments` and `gh pr diff <number>` for the diff.
- **List external PRs for triage**: `gh pr list --state open --json number,title,body,labels,author,authorAssociation,comments` then keep only `authorAssociation` of `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR`, or `NONE` (drop `OWNER`/`MEMBER`/`COLLABORATOR`).
- **Comment / label / close**: `gh pr comment`, `gh pr edit --add-label`/`--remove-label`, `gh pr close`.

GitHub shares one number space across issues and PRs, so a bare `#42` may be either — resolve with `gh pr view 42` and fall back to `gh issue view 42`.

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --comments`.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. `gh issue create --label wayfinder:map`.
- **Child ticket**: create every accepted implementation candidate with `scripts/wayfinder-create-child.sh <map-number> <type> <title> <body-file>`. The command creates the issue, applies `wayfinder:<type>`, links the native GitHub sub-issue, and verifies `parent_issue_url`. Do not use raw `gh issue create` for map children. Where sub-issues aren't enabled, add the child to a task list in the map body and put `Part of #<map>` at the top of the child body. Once claimed, the ticket is assigned to the driving dev.
- **Child traceability**: record the exact `scripts/wayfinder-create-child.sh` invocation, returned child URL, and successful native-link check in the audit ledger or final handoff for every candidate dispositioned `implement`. Re-run `scripts/wayfinder-verify-child.sh <map-number> <child-number>` before claiming a frontier child; its output is the parent-link and `wayfinder:`-label evidence. Verify every created child, even when only one child will be claimed this session.
- **Blocking**: GitHub's **native issue dependencies** — the canonical, UI-visible representation. Add an edge with `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, where `<blocker-db-id>` is the blocker's numeric **database id** (`gh api repos/<owner>/<repo>/issues/<n> --jq .id`, _not_ the `#number` or `node_id`). GitHub reports `issue_dependencies_summary.blocked_by` (open blockers only — the live gate). Where dependencies aren't available, fall back to a `Blocked by: #<n>, #<n>` line at the top of the child body. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children (`gh issue list --state open`, scoped to the map's sub-issues / task list), drop any with an open blocker (`issue_dependencies_summary.blocked_by > 0`, or an open issue in the `Blocked by` line) or an assignee; first in map order wins.
- **Claim**: `gh issue edit <n> --add-assignee @me` — the session's first write.
- **Resolve**: `gh issue comment <n> --body "<answer>"`, then `gh issue close <n>`, then append a context pointer (gist + link) to the map's Decisions-so-far.

### Session lifecycle

Map body is workflow authority. Handoffs record evidence and state; they do not
replace map policy or prescribe a stop after an empty frontier.

1. Load map and handoff, then query native child state.
2. If an open, unblocked, unassigned child exists, claim exactly one before work
   and resolve it through verification, tracker resolution, and map update.
3. If frontier is empty, run the map's required focused or full audit. Do not
   create a checkpoint-only session.
4. Advance each retained candidate through evidence, exploration, falsification,
   verifier packet, verification, and disposition. The packet records mode, model,
   blind position, L1-L5 results, deterministic-gate result, HARD/SOFT triage,
   confidence, and artifact pointer. Create one child for every in-scope candidate
   dispositioned `implement`, using the wrapper and recording the command in the
   audit ledger or handoff. Verify each native parent link. Claim and resolve
   exactly one frontier child per session. After that child is resolved, write the
   successor handoff; do not continue with another child in the same session.
5. If audit finds no defensible candidate, record the clean-audit evidence and
   stop. If human input is required, create `needs-info` or `ready-for-human`
   issue with facts, decision, blocker, and smallest safe next action.

The candidate lifecycle is:

`identified -> evidenced -> explored -> falsified -> verified -> dispositioned -> ticketed -> implemented -> re-audited`

### Human decision deferral

The agent never asks the user a question or invokes the question tool. If a decision
needs human input, create a separate issue instead. Label it `needs-info` when facts or
requirements are missing, or `ready-for-human` when facts are complete and only an
explicit preference or approval is needed. Include verified facts, the exact decision,
why execution is blocked, and the smallest safe next action. Leave it unassigned unless
 the tracker requires an owner. Continue unrelated AFK work; if none exists, write the
 handoff and stop.

### Decision handoff lifecycle

Human decision issues stay open after the human answers so another agent can consume the answer.
The human-resolution transition is:

1. Add the decision as a comment with the chosen policy and constraints.
2. Remove `needs-info` or `ready-for-human`.
3. Add `ready-for-agent`.
4. Remove any stale human or previous-agent assignee.
5. Leave the issue open for the next agent to claim.

The next agent claims the issue, implements or records the decision, updates the Map or parent
pointer, then closes the issue. Decision-only issues close only after their decision pointer is
recorded. An open assigned issue with a human-resolution comment is stale state: reconcile it
before frontier selection instead of dropping it permanently.
