# Frontend-Rebuild Ticket Tracking

Work "blocked by" first. Unblocked tickets are ready to grab.

## Blocking edges

| Ticket | Blocked by | Status |
|--------|-----------|--------|
| 01 — Merge V3 migration into V1 | — | [x] built¹ |
| 02 — Rename VIEWER→ONBOARDING | — | [x] built¹ |
| 03 — GET /api/me + /api/me/capabilities | — | [x] built¹ |
| 04 — DESIGN.md + LinearTheme | — | [x] built¹ |
| 05 — MockEngine in ApiClient | — | [x] built¹ |
| 06 — NavHost routing | 04 | [ ] |
| 07 — Login + capabilities fetch | 03, 05, 06 | [ ] |
| 08 — Branch selector + clock-in | 06, 07 | [ ] |
| 09 — Session dashboard | 06, 07 | [ ] |
| 10 — Session detail + editing | 09 | [ ] |
| 11 — Navigation drawer | 07 | [ ] |
| 12 — Client search | 11 | [ ] |
| 13 — Inventory + product sale | 11 | [ ] |
| 14 — Finance + remittances + reports | 11 | [ ] |
| 15 — Notifications + audit log + user mgmt | 11 | [ ] |

¹ Built on branch; quality issues tracked by CR-031, CR-032, CR-033, CR-034.

## Dependency graph

```
01-05 (built)
   │
06 ← blocks 07,08,09
07 ← blocks 08,09,11
09 ← blocks 10
11 ← blocks 12,13,14,15
```

## Unblocked right now

06 (needs CR-031, CR-033 fixed first for the LinearTheme it depends on)

## To mark done

Append `**Status:** ✅ done` to the ticket file, check `[x]` in table above.
