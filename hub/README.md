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

## The laptop program (Windows)

`company-hub.exe`, built by `.github/workflows/hub.yml` and put on the `site-hub` release. Opened
with no arguments it is the laptop program (`laptop.go`, Windows parts in `laptop_windows.go`):

- First start: copies itself to `%AppData%\CompanyHub\`, puts "Company site hub" on the desktop,
  asks Windows once for admin rights to add the firewall rule `CompanySiteHub` and the hosts line
  `192.168.137.1 hub.kiel.party`, then asks an Owner or Manager to sign in once. The server answers
  a laptop token (`POST /api/site-hub/laptops`), good for a year and renewed with each certificate.
- Hotspot: `hotspot.ps1`, the WinRT tethering API, which starts with no internet (the Settings
  switch refuses). Name "Company Site Hub", password made once per laptop, shown in the window.
- DNS (`dns.go`) on the hotspot address, port 53: `hub.kiel.party` is the laptop, the iCloud Private
  Relay names don't exist (Relay turns off for this Wi-Fi), others go to 1.1.1.1 / 8.8.8.8 when
  online. If Windows holds port 53 itself, the hosts line is the fallback. The window's "Name check"
  line says which one answered.
- Certificate (`cert.go`): its own Let's Encrypt certificate by DNS-01; the key stays on the laptop.
  The server writes the `_acme-challenge` record (`PUT /site-hub/challenge`) when it holds
  `CLOUDFLARE_DNS_TOKEN`; until then the window says "none yet". Renewed at two thirds of its life.
- Keeps the laptop awake while the window is open and reports its battery to the phones, which
  warn at 15%. Status goes to the window and `report.txt`, events to `hub.log`, both in the folder.

Tests: `go test` (Linux, plus Pebble for the certificate path) and the Windows runner
(`laptop_windows_test.go`). Not testable in CI: a real Wi-Fi hotspot and phones (owner's try,
`~/Work/tries/company-hub/build.md`).
