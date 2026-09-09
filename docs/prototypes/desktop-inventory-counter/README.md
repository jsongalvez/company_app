# Desktop prototype: inventory-counter (Stock Counter)

Inventory-first dashboard: the home shelf is a physical count ledger, not a
schedule. Counts, low-stock flags, product remittance link, and unit x quantity
math are visible on every screen. Fake data only — no network, no backend.

## Run

```bash
COMPANYAPP_PROTO_INVENTORY_COUNTER=true ./gradlew :composeApp:run
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
- [ ] Branch select: per-branch SKU + low-stock counts, day-status flags
- [ ] Count ledger (home): system vs counted per SKU, MATCH / LOW flags,
      variance + shelf value (unit x quantity) math, key-in/recount a count
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
- [ ] Audit log: counts, voids, submits, undos all land here
- [ ] Profile: capabilities, clock-out, logout, exit prototype

## Theme notes

Warehouse stencil ledger: kraft shelves (`#EFE6D2`), ink stencil headers,
safety-orange (`#D9531E`) low-stock flags, SKU barcode chips, ledger rows for
all money math. Dark ink nav rail with an orange active tile. Deliberately
distinct from Linear and sibling prototypes — stock first, sessions second.
