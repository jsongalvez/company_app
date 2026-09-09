# Desktop prototype: session-scribe

Fast session logging desk — minimal taps, smart defaults from client history, practitioner flow-optimized.
Fake data only. No backend, no network.

## Run

```bash
COMPANYAPP_PROTO_SESSION_SCRIBE=true ./gradlew :composeApp:run
```

Compile check:

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens 1280x800, full-screen capable. Unset the env var to return to the real app.

## Flow checklist

- Login with prefilled practitioner, or preview the ONBOARDING locked welcome (zero capabilities note).
- Branch select across Quiapo (OPEN), Marikina (PAST), Lingap (REMITTED).
- Today: pending queue with one-tap Done / No-show, clock-in home vs relief view-only note, invite + relief request.
- Sessions: quick-log bar picks a client and auto-fills type + price from history; ledger filters
  PENDING / COMPLETED / NO_SHOW / CANCELLED; detail supports complete / no-show / cancel plus
  void with required reason and unvoid.
- Rule notes visible: walk-in sessions cannot be NO_SHOW or CANCELLED; each client holds at most
  one PENDING session.
- Clients: global list with search, pending/clear tags, anonymized row keeps gender + age only.
- Finance: session net vs product gross, SESSION + PRODUCT drafts, submit freezes a snapshot,
  Undo-48h with reason; commission split note (pooled per branch day, split equally among
  practitioners + coordinators clocked in at sold_at).
- Branch-day banner shows OPEN / PAST / REMITTED with the 04:00 Asia/Manila boundary note.
- Team: on-duty roster in slot order, relief inbox accept/grant/decline, revoke on decided rows.
- Mailbox: read/unread toggles, history kept. Audit log: every mutation with actor + reason.
- Profile: clock in/out plus logout back to login.

## Theme notes

Scribe desk: cream paper canvas, ink-black rail with amber highlighter selection, mono scribe codes,
big 44-48dp tap targets for the queue. Distinct from Linear and from sibling prototypes — built for
a practitioner standing at the desk, scribing the day line by line.
