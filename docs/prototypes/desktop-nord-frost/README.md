# Desktop prototype: nord-frost

Nord frost — Arctic blues, snow surfaces, crisp clinical chill. A luminous
snow-light desk: near-white snow canvas, thin frost-blue borders, soft rounded
snow-drift panels, uppercase micro-labels with wide tracking, ❄ ◇ ● ○ motifs.
Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_NORD_FROST=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any keeper name works, `STEP INSIDE` goes to branch select.
- [ ] ONBOARDING: `I AM ONBOARDING` shows the sealed ice door (empty capability bundle note).
- [ ] Branch select: pick Aurora Central, Glacier North, or Snowline East.
- [ ] Home: clock in/out on the home drift; request cover / send invite with a note; relief board lists duty, invites, requests.
- [ ] Sessions: filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule note shown; void with reason; restore; complete; admit a walk-in.
- [ ] Clients: global registry; at-most-one-PENDING note; veil/unveil toggle per client.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from the Profile lever.
- [ ] Finance: SESSION and PRODUCT vaults with drafts; seal submits a snapshot; thaw (undo) within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row; watch glance card.
- [ ] Mailbox: unread count, open-one and open-all, drift day per letter.
- [ ] Audit trail: clock / complete / void / submit / undo / walk-in / relief / lever events append entries with reasons.
- [ ] Profile: Branch Day lever, clock in/out, reset frost, logout.

## Theme notes

- Palette: snow `#ECEFF4` background, snow-soft `#E5E9F0` rail, drift `#D8DEE9` borders, panel `#F7F9FC`, polar night `#2E3440` text, slate `#4C566A`, mist `#7B8AA0`, frost `#88C0D0`, frost-deep `#81A1C1`, glacier `#5E81AC`, teal `#8FBCBB` / teal-ink `#2F6B6B`, amber `#B9882F`, rose `#B45F6D`.
- Shape: soft 9–12dp rounded snow-drift corners, 1dp frost borders, pill status tags — airy clinical calm against denser siblings.
- Copy: cryo-desk language ("FROST DESK", "HOME DRIFT", "SETTLE", "ON WATCH", "SEAL SNAPSHOT", "THAW", "VEIL/UNVEIL", vault/drift/hall renamed throughout).
- Motifs: `❄ ◇ ● ○ ▓` frost glyphs, thin double frost rules under the marquee, tracked uppercase micro-labels.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/nord-frost/*.desktop.kt` (package `...proto.nordfrost`), fake `NordFrostFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
