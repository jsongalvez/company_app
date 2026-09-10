# Desktop prototype: clerk-counter (Scan Lane)

Stock-first counter view for the inventory clerk: the home lane is a scan feed,
not a schedule. Every SKU is a scan slip with system vs counted tape, stamped
variance callouts (MATCH / SHORT / OVER / LOW), and unit x quantity lane value.
Fake data only — no network, no backend.

## Run

```bash
COMPANYAPP_PROTO_CLERK_COUNTER=true ./gradlew :composeApp:run
```

Compile check (targeted, warm once):

```bash
./gradlew :composeApp:compileKotlinDesktop
```

Window opens at 1280x800, full-screen capable. Without the env var, the real
`App()` runs unchanged.

## Flow checklist

- [ ] Login rail: sign in as Practitioner / Coordinator / MANAGER / Accountant
- [ ] ONBOARDING locked: sign in as Eli Santos → lock gate, switch user back
- [ ] Branch select: per-branch SKU + variance-flag counts, day-status flags
- [ ] Scan lane (home): system vs counted per slip, MATCH / SHORT / OVER stamps,
      LOW flags, lane value (unit x quantity) math, scan/rescan a count
- [ ] Clock-in home + relief duty / invite / request board with accept/decline
- [ ] Sessions: roster + book PENDING, open detail, complete / no-show / cancel
- [ ] Walk-in rule note: walk-in s-03 ignores NO_SHOW / CANCELLED moves
- [ ] Session detail: linked products with unit x quantity math + link-a-sale,
      void with required reason / unvoid
- [ ] Clients: global record, at-most-one-PENDING flags, anonymized view
- [ ] Branch-day banner: OPEN / PAST / REMITTED + 04:00 Asia/Manila boundary
- [ ] Finance: live product math card, SESSION + PRODUCT draft / submit /
      snapshot, Undo within 48h with reason, commission split note
- [ ] Team: users, roles, capability bundles, branch slot order
- [ ] Mailbox: read / unread, history kept
- [ ] Audit log: scans, voids, submits, undos all land here
- [ ] Profile: capabilities, clock-out, logout, exit prototype

## Theme notes

Till receipt + scan lane: warm paper (`#F6F4EC`) counter, dark till header
(`#22303C`), teal scan beam (`#0E7C6B`) on monospace SCAN chips, stamped variance
callouts (SHORT red, OVER amber, MATCH green, LOW burnt-orange) with perforation
rules between tape rows. Top till bar + horizontal lane tabs, slips numbered
sequentially. Deliberately distinct from Linear and sibling prototypes —
scan first, sessions second.
