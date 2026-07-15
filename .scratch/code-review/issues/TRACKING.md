# CR Ticket Tracking

Work "blocked-by" first — any unblocked ticket is ready to grab.

## Blocking edges

| Ticket | Blocked by | Status |
|--------|-----------|--------|
| CR-001 — runCatching on path params | — | [ ] |
| CR-002 — JVM clock in service/repo | — | [ ] |
| CR-003 — audit logs not atomic | — | [ ] |
| CR-004 — capability gates (chunk 1 only) | — | [ ] |
| CR-005 — magic number priorities | — | [ ] |
| CR-006 — commission recalc on clock-in/out | — | [ ] |
| CR-007 — ClockInRequest field name | — | [ ] |
| CR-008 — TOCTOU race | — | [ ] |
| CR-009 — deduplicate test helpers | — | [ ] |
| CR-011 — GLOBAL→BRANCH context | — | [x] |
| CR-012 — scheduler queries roles | — | [x] |
| CR-013 — commission recalc verification | CR-006 | [ ] |
| CR-014 — pg_trgm ILIKE search | — | [x] |
| CR-015 — ViewModel boilerplate | — | [ ] |
| CR-016 — JVM clock (all chunks) | — | [ ] |
| CR-017 — capability gates (all chunks) | — | [x] |
| CR-018 — FOR UPDATE on client+inventory | — | [x] |
| CR-019 — base rate gaps + PII + pricing | — | [ ] |
| CR-020 — assertEditable + expectedVersion | — | [x] |
| CR-021 — commission recalc endpoint + net_income view | CR-013 | [ ] |
| CR-022 — soft-deleted expenses + duplicate mapping | — | [ ] |
| CR-023 — `!!` in runCatching + no-zone OffsetDateTime | — | [ ] |
| CR-024 — audit in repo + snapshot transaction | — | [ ] |
| CR-025 — magic strings + duplicated plumbing | — | [ ] |
| CR-026 — test anti-patterns | — | [ ] |
| CR-027 — N+1 scheduler + notification table | — | [ ] |
| CR-028 — business logic in route + fake DTOs | — | [ ] |
| CR-029 — composeApp errors-as-logInfo + logback regression | — | [ ] |
| CR-030 — onUnauthorized + dead code | — | [ ] |
| CR-031 — null-safety + LinearTheme tokens | — | [ ] |
| CR-032 — MeService dup query + INACTIVE guard | — | [x] |
| CR-033 — Inter font + rounded/spacing scales | — | [ ] |
| CR-034 — ApiClient config + speculative params | — | [ ] |
| CR-035 — chunk2a duplicated patterns | — | [ ] |

## Overlaps to merge or de-dupe

- **CR-032 / CR-034** both mention "MeService duplicates capability query" — check if 032 already covered it
- **CR-002 / CR-016** both are "JVM clock" — CR-002 is chunk-1 only, CR-016 covers remaining chunks. If you did both, mark both.
- **CR-004 / CR-017** both are "capability gates" — CR-004 is chunk-1 only, CR-017 covers remaining chunks.

## To mark done

Append `\n**Status:** ✅ done` to the bottom of the ticket file, then check `[x]` above.
