# Change history (Audit)

Route `#/audit`. Read-only list of who changed what, and when. Filters live in the address (`?table=&action=&who=&last=&from=&to=`).

## Sub-features
- Filters: table ("Everything"), action ("Any change"), person, time range ("Any time" picker with presets Last 15 min .. Last 30 days).
- Flagged changes that need acknowledging (from `/api/audit-log/flagged`).
- Rows for inserts, updates, deletes with record and actor.

## How to get to it (user POV)
Side menu, ADMIN, "Change history". Owner/admin only.

## Driving it with `tools/verify/drive.mjs`
`tools/verify/verify.sh shot '#/audit'`. For a filtered view add the query: `shot '#/audit?last=1d&action=UPDATE'`.
To see real rows, make a change first (clock in, add a client) so the audit log has entries, then open the page.

## Gotchas
- On a fresh scratch DB the helper's own clock-in may or may not have produced rows yet; the empty state reads "No changes match." Both are valid, so state which you saw.
- Desktop first load can land on `#/today` if the route is set before sign-in finishes; `drive.mjs` waits for the hash to settle first.
