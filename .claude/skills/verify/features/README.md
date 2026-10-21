# Feature map

One file per user-facing page. Each says how to reach it, how to drive it, and what proves it works.
Routes are hash routes; open them with `tools/verify/verify.sh shot '#/<route>'`.
Status says what has actually been driven, not what should work.

| Page | Route | Status |
| --- | --- | --- |
| [Sessions (Today)](today.md) | `#/today` | driven, desktop and phone |
| [Change history (Audit)](audit.md) | `#/audit` | driven, desktop and phone |
| [Clients](clients.md) | `#/clients` | mapped, not yet driven |

Other routes in the app (read `web/src/App.svelte` for the list): Stock, Expenses & pay, Sales reports, Remittance, My profile,
My shifts & pay, Relief duty, Alerts, Users, Branches, Products, Feedback inbox. Add a file here when you first drive one.
