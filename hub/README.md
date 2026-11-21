# Site hub relay

On a provincial tour or medical mission with no signal, the laptop runs this relay on its own
hotspot as `https://hub.kiel.party`. Phones on the hotspot mirror their day's saves to it, sealed
with a key only the branch's members hold, and read each other's: the Site panel on Today
(`web/src/lib/site.svelte.ts`, `web/src/components/SitePanel.svelte`). The cloud stays the only
judge; each phone still sends its own saves when the signal is back.

- Room and key: the server derives both from its secret per branch and day and hands them to
  members in the Today answer (`backend/.../branchday/SiteHub.kt`). The room id is the only
  credential the relay sees; it cannot open the records.
- Memory only. A restart loses everything and the phones send it all again (new `boot` id).
- Caps: 1 MB a call, 16 KB a record, 20,000 records and 64 phones a room, 256 rooms, 14 days.
- `go test` runs inside the Docker build. `hub/e2e-up.sh <app origin>` serves it with a test cert
  for `web/tests/site-hub.e2e.ts`.

Not built yet (slice 2): the Windows laptop program that starts the hotspot, answers DNS for
`hub.kiel.party`, fetches the real cert from the cloud, keeps the laptop awake and shows battery.
Design: `~/Work/tries/company-hub/design.md`.
