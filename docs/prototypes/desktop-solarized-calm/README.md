# Desktop prototype: solarized-calm

Solarized calm. Exact Solarized palette precision on a warm paper ledger:
base3 `#FDF6E3` background, base2 `#EEE8D5` sheets, base01/base00 quiet text,
blue `#268BD2` primary, cyan `#2AA198` secondary, green/yellow/violet day dots.
Low-contrast serenity for long shifts — monospace ledger rows, etched section
rules, tonal status chips instead of loud badges. Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_SOLARIZED_CALM=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Enter calmly` goes to branch select.
- [ ] ONBOARDING: `Preview the ONBOARDING welcome` shows the locked mat (empty Capability bundle note).
- [ ] Branch select: pick Solmar Clinic (OPEN), Cove Annex (PAST), or Dune Outreach (REMITTED).
- [ ] Home: clock in/out; today ledger counts; relief invites accept/decline plus ask-for-relief form.
- [ ] Sessions: filter all / pending / done / no-show / voided; walk-in rule note shown; tap rows; void with reason; restore with reason.
- [ ] Clients: global list; at-most-one-PENDING note; names veiled by default, tap to reveal.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; move the day from Profile.
- [ ] Finance: SESSION (net income) and PRODUCT (price x quantity) drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row; Capability glance card.
- [ ] Mailbox: read/unread dots, tap toggles, mark-all-read.
- [ ] Audit: void / unvoid / submit / undo / clock / relief entries with reasons.
- [ ] Profile: clock in/out, move the day, reset demo, log out.

## Theme notes

- Palette: exact Solarized values — base3 `#FDF6E3`, base2 `#EEE8D5`, base1 `#93A1A1`, base00 `#657B83`, base01 `#586E75`, base02 `#073642`, blue `#268BD2`, cyan `#2AA198`, green `#859900`, yellow `#B58900`, orange `#CB4B16`, red `#DC322F`, violet `#6C71C4`.
- Shape: 6-10dp quiet sheets, etched centered section rules, tonal chips with mono labels, no elevation, no loud badges.
- Type: sans headings, monospace ledger meta (`FontFamily.Monospace`), small quiet copy, 720dp-centered column, day-dot header.
- Copy: ledger language ("The ledger is clear", "Seat a walk-in", "Seal snapshot", "Move the day").
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/solarized-calm/*.desktop.kt` (package `...proto.solarizedcalm`), fake `CalmFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
