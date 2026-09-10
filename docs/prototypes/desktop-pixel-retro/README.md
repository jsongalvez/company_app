# Desktop prototype: pixel-retro

8-bit pixel retro arcade. Near-black void canvas, chunky 3dp neon borders with
hard offset shadows, monospace uppercase headers with wide tracking, blocky
glyph icons (▶ ▷ ■ ▣ ◆ ♥ ★), zero rounded corners — insert-coin energy with
chiptune restraint in the copy. Fake data only — no network calls.

## Run

```bash
COMPANYAPP_PROTO_PIXEL_RETRO=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800 and can go full-screen. Without the env var the real `App()` boots unchanged.

## Flow checklist

- [ ] Login: any name/mail works, `PRESS START` goes to stage select.
- [ ] ONBOARDING: `VIEW THE LOCKED DOOR` shows the sealed stage (empty capability bundle note).
- [ ] Branch select: pick Pixel Plaza, Sprite Springs, or Boss Keep.
- [ ] Home: clock in/out at the home branch; request/invite relief with a note; relief board lists duty, invites, requests.
- [ ] Quests: filter ALL / PENDING / COMPLETED / NO_SHOW / CANCELLED; walk-in rule note shown; void with reason; unvoid; complete; admit a fresh walk-in.
- [ ] Party list: global registry; at-most-one-PENDING note; mask/reveal toggle per client.
- [ ] Branch-day banner: OPEN / PAST / REMITTED copy plus the 04:00 Asia/Manila boundary note; flip states from Player card.
- [ ] Coffers: SESSION and PRODUCT ledgers with drafts; submit seals a snapshot; undo within 48h with reason; commission-split note.
- [ ] Guild: users and roles incl. ONBOARDING locked row; rank glance card.
- [ ] Mailbox: unread count with sealed/open markers, read-one and open-all, delivery day per letter.
- [ ] Quest log: void / unvoid / complete / submit / undo / walk-in / clock events append entries with reasons.
- [ ] Player: Branch Day lever, clock in/out, reset demo, logout.

## Theme notes

- Palette: void `#0F0F1B` background, panel `#1D2B53`, hi-panel `#29366F`, cream `#FFF1E8` text, dim mauve `#83769C`, sprite green `#00E436`, coin yellow `#FFEC27`, heart pink `#FF77A8`, laser cyan `#29ADFF`, game-over red `#FF004D`, continue orange `#FFA300`, boss purple `#7E2553`.
- Shape: sharp rectangles everywhere, 3dp borders, hard 4dp offset drop shadows on buttons — chunky cartridge feel against bubbly siblings.
- Copy: arcade language ("PRESS START", "SELECT YOUR STAGE", "HI-SCORE", "1 CREDIT", quest/coffer/guild/mailbox renamed to quests/coffers/guild/mailbox with flavor).
- Motifs: `▶ ▷ ■ ▢ ▣ ◆ ♥ ★ ◄► ▓` block glyphs, double neon rules under the marquee, uppercase tracked headers.
- Files: `composeApp/src/desktopMain/kotlin/com/companyb/companyapp/proto/pixel-retro/*.desktop.kt` (package `...proto.pixelretro`), fake `PixelRetroFakeRepo` only, no `ApiClient`/Ktor/backend imports.

## Screenshots

Human judges by running the prototype above; capture window shots per flow when reviewing.
