# Desktop prototype: a11y-max

Max accessibility. Pure-black canvas, white 21:1 text, yellow focus armor on every
control, body text starting at 16sp with three instant text sizes, a spoken-word
announcement strip wired as an assertive live region, and the full keyboard map on
a single help dialog. No Linear chrome left. Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_A11Y_MAX=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any email works, `Sign in` goes to branch select.
- [ ] ONBOARDING: `Preview the ONBOARDING locked account` shows the zero-capability lock note.
- [ ] Branch select: pick Sunrise Clinic, Harbor Provincial Tour, or Lingap Medical Mission.
- [ ] Home: clock in/out; relief duty/request/invite rows with grant/revoke; broadcast a relief request; send a relief invite.
- [ ] Sessions: All / PENDING / COMPLETED / NO_SHOW / CANCELLED filter pills; walk-in rule note shown and enforced (no NO_SHOW/CANCELLED buttons on walk-ins); complete/reopen; void with required reason; unvoid.
- [ ] Clients: global list with search; at-most-one-PENDING note; anonymized view toggle plus per-record anonymize (keeps gender and age).
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Profile.
- [ ] Finance: SESSION and PRODUCT drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Team: users and roles incl. ONBOARDING locked card with one-tap Practitioner grant; role capability blurbs.
- [ ] Mailbox: read/unread toggles, mark-all-read, relief items name branch and day; unread count rides the MAIL tab.
- [ ] Audit: clock, relief, void/unvoid, submit/undo append entries with reasons, newest first.
- [ ] Profile: text size Standard/Large/Extra-large, branch-day flip, clock out, log out, reset demo data.

## Theme notes

- Palette: black `#000000` canvas, near-black `#0D0D0D` panels, white `#FFFFFF` paper cards with black ink, focus yellow `#FFD60A` (banner, selected tabs, focus rings, skip link), green `#00E676` / red `#FF5252` status always paired with text labels, never color alone.
- Shape: 6–10dp corners, 2–4dp borders everywhere; selected tabs are solid yellow, unselected are white-outlined; every button is at least 52dp tall.
- Copy: plain-language, full sentences, every action narrated into the announcement strip ("Session for Ana Villanueva is now COMPLETED.").
- Layout: yellow branch-day banner, assertive live-region strip, skip-to-content link, two-row tab bar with Alt+number shortcuts, single-column scrolled sections.
- Keyboard: Alt+1..8 jump sections, `?` opens the map, Esc closes dialogs, Tab/Shift+Tab reach everything, Enter activates. Focus rings are 4dp yellow and dialogs announce close with focus held in-section.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/a11y-max/*.desktop.kt` (package `...proto.a11ymax`), fake `A11yMaxFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
