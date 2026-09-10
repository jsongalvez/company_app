# Desktop prototype: quest-log (RPG quest log)

Ticket #838 · part of map #755. Isolated fake-data branch — never push to master from here.

## Theme

The working day is a **quest chain**: sessions are quests pinned to the board, commission is
**XP**, and turned-in quests level the hero up. Tavern-at-night look — deep ink browns,
parchment ink, gold headings, rarity-ranked quest badges (S/A/B/C by bounty). Canonical domain
terms stay on screen verbatim (PENDING / COMPLETED / NO_SHOW / CANCELLED, Branch Day
OPEN / PAST / REMITTED, SESSION / PRODUCT remittance, Void, Commission Split, Relief
Duty / Request / Invite, Notification, Audit Log) with quest flavor as the frame.

## Run

```bash
COMPANYAPP_PROTO_QUEST_LOG=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 ("CompanyApp // quest-log"), full-screen capable. Without the env var
the app boots the real `App()` unchanged. Fake data lives in `QuestLogFake.desktop.kt`
(`QuestLogRepo`); no `ApiClient`, no Ktor, no backend imports, no network calls.

## Flow checklist (all clickable, fake data)

- [ ] Guild-hall door (login): pick a traveler (MANAGER / Coordinator / Practitioner /
      ONBOARDING), speak any callsign, cross the threshold.
- [ ] Squire's bench (ONBOARDING locked): K. Dela Pena sees only the trial checklist; can
      switch traveler but never reach the board.
- [ ] Crossroads (branch select): Makati / Cebu Tour / Tondo Mission, then ride out.
- [ ] Day chain banner: 2026-09-08 REMITTED / 2026-09-09 OPEN / 2026-09-10 PAST chips;
      04:00 Asia/Manila boundary note; sealed links need MANAGER/Coordinator to rewrite.
- [ ] Quest board (home): clock in/out (AT CAMP ↔ IN THE FIELD), party-on-duty roster,
      relief horns grant/decline, send invite / request cover, hero level + XP bar.
- [ ] Quests (sessions): ALL/PENDING/COMPLETED/NO_SHOW/CANCELLED filters, quest detail,
      turn in / abandon / recall, void with required reason, lift the void, post a new quest.
      Ranger law: WALK-IN quests bar NO_SHOW/CANCELLED (buttons stay sheathed).
- [ ] Hall of allies (clients): global registry, at-most-one-PENDING horn per ally,
      per-ally quest history, veiled (anonymized) global view + per-ally veil toggle.
- [ ] Vault (finance): SESSION scroll (expense/comp editable, net, seal), PRODUCT scroll
      (add/strike wares, draft total, seal), sealed snapshots, Unseal within 48h only,
      commission split note (wardens 60% / hall 40%).
- [ ] Guild roster (team): users, roles, capabilities, rank blurbs.
- [ ] Raven post: notifications read on click / shush-all, unread count; full audit
      chronicle (every deed above appends a line).
- [ ] Hero's mirror (profile): rank, level, capabilities, outpost; clock in/out, change
      outpost, ride home (logout → back to the guild door).

## Files

`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/quest-log/`
(`package com.companyb.companyapp.proto.questlog`):

- `QuestLogTheme.desktop.kt` — palette, serif type, `QuestLogTheme`.
- `QuestLogFake.desktop.kt` — domain enums/data, `QuestLogRepo` fake ledger, XP/level math.
- `QuestLogChrome.desktop.kt` — cards, badges, day banner, nav rail, status bar, buttons.
- `QuestLogApp.desktop.kt` — `QuestLogApp()` gate flow (auth → onboarding → branch → shell).
- `QuestLogAuth.desktop.kt` — guild door, squire's bench, crossroads.
- `QuestLogHome.desktop.kt` — war camp: muster, party, relief horns, level.
- `QuestLogSessions.desktop.kt` — roster, detail actions, void + create dialogs.
- `QuestLogClients.desktop.kt` — hall of allies + veil.
- `QuestLogFinance.desktop.kt` — vault: SESSION/PRODUCT scrolls, snapshots, tithe.
- `QuestLogTeam.desktop.kt` — guild roster.
- `QuestLogMailbox.desktop.kt` — ravens + audit chronicle.
- `QuestLogProfile.desktop.kt` — hero's mirror + sign-out.

Plus the additive `Main.kt` env gate (`COMPANYAPP_PROTO_QUEST_LOG=true` → `QuestLogApp()`).

## Screenshots

Not captured in this environment (headless). Run the command above and judge the board live.
