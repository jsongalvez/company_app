# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.

## Priority labels

Orthogonal to the roles above: `priority:P0` (do first), `priority:P1`
(valuable, no rush), `priority:P2` (backlog). Absent means P2. Set once at
triage or ticket creation with the reasoning recorded on the ticket; the
wayfinder frontier query (`docs/agents/issue-tracker.md`) picks the
highest-ranked claimable child and never re-derives priority per session.

Edit the right-hand column to match whatever vocabulary you actually use.
