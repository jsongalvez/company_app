# Clients

Route `#/clients`. List and search of clients; each opens `#/clients/<id>` with history of sessions and concerns.

## Sub-features
- Search by name or phone (top bar "Find client by name or phone", and on this page).
- Client detail: past sessions, concerns, booked visits.

## How to get to it (user POV)
Side menu, BRANCH, "Clients"; or the top-bar search.

## Driving it with `tools/verify/drive.mjs`
`tools/verify/verify.sh shot '#/clients'`. Not yet driven. The scratch DB has no clients; create one with the New session flow in
`web/tests/support.ts` (`doneSessionForNewClient`) before checking the detail page.

## Gotchas
- Status: mapped from the side menu and test helpers, not run. First person to drive it: confirm the route and fix this file.
