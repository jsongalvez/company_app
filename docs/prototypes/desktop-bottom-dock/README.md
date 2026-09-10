# Desktop Bottom-Dock Prototype (fake data, branch `prototype/desktop-bottom-dock`)

macOS-style bottom dock shell with a playful stage above it. Part of map #755, ticket #810.

## Run

```bash
COMPANYAPP_PROTO_BOTTOM_DOCK=true ./gradlew :composeApp:run
```

Compile only (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280×800 minimum and goes full-screen through normal OS controls.
No backend, no network: everything lives in `DockFakeRepo` in
`composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/bottom-dock/`.
The `Main.kt` gate is additive — without the env var the real `App()` runs unchanged.

## Flow checklist

1. **Welcome** — locked ONBOARDING account (0 capabilities). "Try the dock" is
   blocked; "Grant Practitioner" simulates the MANAGE_USERS grant and continues.
2. **Sign in** — fake directory of five users (ONBOARDING → ACCOUNTANT). ONBOARDING
   stays unselectable for entry; everyone else pins the dock and clocks in.
3. **Branch** — QC Central (CLINIC), Laguna Tour Stop 3 (PROVINCIAL_TOUR),
   Tondo Medical Mission (MEDICAL_MISSION). Tap to switch, then open the desktop.
4. **Home** — in-line / banked / ticket stat cards, clock in/out, relief invites
   (Yes/No), relief requests (Allow/Deny, pull back own, ask another branch),
   shortcut to the New window.
5. **New (3 windows)** — window 1 who (returning guest or typed label),
   window 2 care menu pick + walk-in toggle, window 3 dock ticket (D-044…).
   Opens a PENDING session + client + audit + bell note.
6. **Queue (sessions)** — status filter chips, per-session manager drawer:
   PENDING → COMPLETED / NO_SHOW / CANCELLED, void/unvoid with required reason.
   Walk-in sessions refuse NO_SHOW and CANCELLED with the rule note.
7. **Guests (clients)** — global list, per-guest open-session count
   (at-most-one-PENDING rule note), anonymize/reveal view
   (gender + age kept).
8. **Till (finance)** — SESSION and PRODUCT drawers: draft −/+500 → submit (seals
   immutable snapshot BD-*) → undo-with-reason (48h window note). Commission
   split rule window (pooled per branch day, even split over clocked-in hands,
   relief paid from this drawer).
9. **Crew (team)** — users in branch-slot order with role bundles,
   ONBOARDING locked row, YOU marker.
10. **Bell (notifications)** — read/unread mailbox, hush per item or hush all.
    Unread count badges the dock tile.
11. **Ledger (audit)** — every prototype mutation prepends who/action/target/reason.
12. **Me (profile)** — current user, clock in/out, log out (returns to sign in,
    unpins the dock).
13. **Branch-day pill row** — full-width OPEN / PAST / REMITTED switcher with the
    04:00 Asia/Manila boundary note, always visible under the menu bar.

## Theme notes

Bottom-dock grape-soda desktop: grape `#3A2060` → teal `#0E3F46` wallpaper with
translucent bubbles, black menu bar, warm paper `#F6F1E6` windows with traffic
lights and title bars, sticky-note stat card, candy dock tiles (64dp selected,
52dp rest, running dot, red badge on Bell). Headings 40sp / 34sp, body 17sp.
Top menu bar + pill row, one front window on stage, floating centered dock —
this variant scraps the Linear design for a Katamari-mac toy desktop.
Screenshots: run it and judge — no checked-in images in the prototype branch.
