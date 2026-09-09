# Desktop prototype: deco-gold

Black-gold art deco hall. Obsidian canvas, hairline gold geometry, cream serif
headlines, symmetrical panels, diamond dividers, sharp 2dp corners — cut stone
and brass, nothing bubbly. Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_DECO_GOLD=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any name/email works, `Enter the hall` goes to branch select.
- [ ] ONBOARDING: `View the ONBOARDING chamber` shows the sealed chamber (empty capability bundle note).
- [ ] Branch select: pick Grand Meridian, Gilded Annex, or Night Pavilion.
- [ ] Home: clock in/out at the home branch; request/invite relief with a note; relief board lists requests, invites and duty.
- [ ] Sessions: filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule note shown; entries expand; void with reason; unvoid; complete; admit a fresh walk-in.
- [ ] Clients: global registry; at-most-one-PENDING note; anonymize/reveal toggle.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION and PRODUCT ledgers with drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked row; rank glance card.
- [ ] Mailbox: unread count with sealed/open markers, open-one and open-all, delivery day per letter.
- [ ] Audit: void / unvoid / complete / submit / undo / walk-in / clock events append entries with reasons.
- [ ] Profile: Branch Day lever, clock in/out, reset demo, log out.

## Theme notes

- Palette: obsidian `#0B0A07` background, panel `#14110B`, gold `#C9A227` primary, bright gold `#E8C86A`, cream `#F3EAD3` text, bronze `#9A8A63` muted, emerald `#3FA97C` clocked-in, ruby `#D9534F` danger.
- Shape: sharp 2–4dp corners everywhere (panels 0-radius feel, plaques square) — deco geometry against bubbly siblings.
- Copy: grand-hall language ("Present your card", "Evening program", "House coffers", "Chronicle", "Letter box").
- Motifs: `◆ ◇ ❖` diamond dividers, double gold rules under the marquee, uppercase plaques, serif headlines.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/deco-gold/*.desktop.kt` (package `...proto.decogold`), fake `DecoGoldFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
