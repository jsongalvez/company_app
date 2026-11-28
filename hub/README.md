# Site hub relay

For day-to-day branch operations, tours, or medical missions when Wi-Fi is bad or there is no
signal, a laptop runs this relay on its own hotspot as `https://hub.kiel.party`. Phones on the
hotspot mirror their day's saves to it, sealed with a key only the branch's members hold, and read
each other's: the Site panel on Today (`web/src/lib/site.svelte.ts`,
`web/src/components/SitePanel.svelte`). The cloud stays the only judge; each phone still sends its
own saves when the signal is back. Phones on their own mobile data/hotspot or regular branch Wi-Fi
talk to the cloud directly and work offline on their own as usual.

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
  `192.168.137.1 hub.kiel.party`, then asks any active staff member to sign in once. The server
  records the paired laptop in `site_hub_laptop` (`POST /api/site-hub/laptops`) and answers a
  revocable laptop token. Owners and Managers can list (`GET /api/site-hub/laptops`) or revoke
  (`DELETE /api/site-hub/laptops/{laptopId}`) laptops; a revoked laptop automatically prompts to
  re-pair.
- Hotspot: `hotspot.ps1`, the WinRT tethering API, which starts with no internet (the Settings
  switch refuses). Name "Company Site Hub", password made once per laptop, shown in the window.
- DNS (`dns.go`) on the hotspot address, port 53: `hub.kiel.party` is the laptop, the iCloud Private
  Relay names don't exist (Relay turns off for this Wi-Fi), others go to 1.1.1.1 / 8.8.8.8 when
  online. If Windows holds port 53 itself, the hosts line is the fallback. The window's "Name check"
  line says which one answered.
- Certificate (`cert.go`): the server owns and renews one Let's Encrypt certificate for
  `hub.kiel.party` by DNS-01 when it holds `CLOUDFLARE_DNS_TOKEN` (`SiteHubCerts.kt` /
  `AcmeClient.kt`). Paired laptops pull it via `POST /site-hub/cert` and refresh at two thirds of
  its life, so any number of laptops can pair without touching DNS or hitting Let's Encrypt's
  weekly duplicate-certificate limit.
- Keeps the laptop awake while the window is open and reports its battery to the phones, which
  warn at 15%. Status goes to the window and `report.txt`, events to `hub.log`, both in the folder.

Tests: `go test` (Linux and Windows runners in `.github/workflows/hub.yml`; real ACME issuance is
tested against Pebble in `.github/workflows/quality.yml`). Not testable in CI: a real Wi-Fi
hotspot and phones.
