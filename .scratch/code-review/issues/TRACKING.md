# CR Ticket Tracking

Work "blocked-by" first — any unblocked ticket is ready to grab.

## Blocking edges

| Ticket | Blocked by | Status |
|--------|-----------|--------|
| CR-001 — runCatching on path params | — | [x] |
| CR-002 — JVM clock in service/repo | — | [x] |
| CR-003 — audit logs not atomic | — | [x] |
| CR-004 — capability gates (chunk 1 only) | — | [x] |
| CR-005 — magic number priorities | — | [x] |
| CR-006 — commission recalc on clock-in/out | — | [x] |
| CR-007 — ClockInRequest field name | — | [x] |
| CR-008 — TOCTOU race | — | [x] |
| CR-009 — deduplicate test helpers | — | [x] |
| CR-011 — GLOBAL→BRANCH context | — | [x] |
| CR-012 — scheduler queries roles | — | [x] |
| CR-013 — commission recalc verification | CR-006 | [ ] |
| CR-014 — pg_trgm ILIKE search | — | [x] |
| CR-015 — ViewModel boilerplate | — | [x] |
| CR-016 — JVM clock (all chunks) | — | [x] |
| CR-017 — capability gates (all chunks) | — | [x] |
| CR-018 — FOR UPDATE on client+inventory | — | [x] |
| CR-019 — base rate gaps + PII + pricing | — | [x] |
| CR-020 — assertEditable + expectedVersion | — | [x] |
| CR-021 — commission recalc endpoint + net_income view | CR-013 | [x] |
| CR-022 — soft-deleted expenses + duplicate mapping | — | [x] |
| CR-023 — `!!` in runCatching + no-zone OffsetDateTime | — | [x] |
| CR-024 — audit in repo + snapshot transaction | — | [x] |
| CR-025 — magic strings + duplicated plumbing | — | [x] |
| CR-026 — test anti-patterns | — | [ ] |
| CR-027 — N+1 scheduler + notification table | — | [ ] |
| CR-028 — business logic in route + fake DTOs | — | [x] |
| CR-029 — composeApp errors-as-logInfo + logback regression | — | [x] |
| CR-030 — onUnauthorized + dead code | — | [x] |
| CR-031 — null-safety + LinearTheme tokens | — | [x] |
| CR-032 — MeService dup query + INACTIVE guard | — | [x] |
| CR-033 — Inter font + rounded/spacing scales | — | [ ] |
| CR-034 — ApiClient config + speculative params | — | [ ] |
| CR-035 — chunk2a duplicated patterns | — | [ ] |
| CR-036 — quality gate effectiveness audit | — | [x] |
| CR-050 — extract shared app-boot lifecycle | — | [ ] |
| CR-051 — implement start-app.sh (app-boot lifecycle module) | CR-052 | [ ] |
| CR-052 — extract shared shell library (common.sh) | — | [x] |
| CR-053 — consolidate k6 scripts into a single module | — | [ ] |
| CR-054 — deepen Main.kt init (inject config, isolate dev seeder) | — | [ ] |
<!-- NOTE: finish CR-001–035 (backend) before CR-037–043 (quality gates). -->
<!-- CR-051–054 are architecture deepening from 2026-07-17 review. Order: 052 → 051 (foundation first), 053 parallel, 054 parallel. -->
<!-- Stricter gates add friction; backend fixes establish a clean baseline first. -->
| CR-037 — health check on app boot test | — | [x] |
| CR-038 — wire check-baselines.sh into pre-push | — | [x] |
| CR-039 — replace jmhClasses with JMH run in pre-commit | — | [x] |
| CR-040 — wire k6 baseline into pre-push | CR-037 | [x] |
| CR-041 — shared module compilation + test step | — | [x] |
| CR-042 — iOS compilation target in pre-commit | — | [x] |
| CR-043 — scope ktlintFormat to staged files only | — | [x] |
| CR-044 — extract grantCapability delegation in DatabaseTestHelper | — | [x] |
| CR-045 — scope SessionTable + ClientTable deleteAll() | — | [x] |
| CR-046 — scope product-related deleteAll() | — | [x] |
| CR-047 — scope remaining shared-table deleteAll() | — | [x] |
| CR-048 — remaining ThrowsCount suppressions | — | ❌ wontfix |
| CR-049 — audit LongParameterList suppressions | — | [x] |

## Overlaps to merge or de-dupe

- **CR-032 / CR-034** both mention "MeService duplicates capability query" — check if 032 already covered it
- **CR-002 / CR-016** both are "JVM clock" — CR-002 is chunk-1 only, CR-016 covers remaining chunks. If you did both, mark both.
- **CR-004 / CR-017** both are "capability gates" — CR-004 is chunk-1 only, CR-017 covers remaining chunks.

## GH Issues

Unresolved tickets migrated to GitHub Issues (2026-07-17):

| CR Ticket | GH Issue |
|-----------|----------|
| CR-013 | [#8](https://github.com/jsongalvez/company_app/issues/8) |
| CR-015 | [#9](https://github.com/jsongalvez/company_app/issues/9) |
| CR-019 | [#10](https://github.com/jsongalvez/company_app/issues/10) |
| CR-021 | [#11](https://github.com/jsongalvez/company_app/issues/11) | ✅ done |

| CR-024 | [#12](https://github.com/jsongalvez/company_app/issues/12) |
| CR-026 | [#13](https://github.com/jsongalvez/company_app/issues/13) |
| CR-027 | [#14](https://github.com/jsongalvez/company_app/issues/14) |
| CR-028 | [#15](https://github.com/jsongalvez/company_app/issues/15) |
| CR-029 | [#16](https://github.com/jsongalvez/company_app/issues/16) |
| CR-031 | [#17](https://github.com/jsongalvez/company_app/issues/17) | ✅ done |
| CR-033 | [#18](https://github.com/jsongalvez/company_app/issues/18) |
| CR-034 | [#19](https://github.com/jsongalvez/company_app/issues/19) |
| CR-035 | [#20](https://github.com/jsongalvez/company_app/issues/20) |
| CR-038 | [#21](https://github.com/jsongalvez/company_app/issues/21) |
| CR-039 | [#22](https://github.com/jsongalvez/company_app/issues/22) |
| CR-040 | [#23](https://github.com/jsongalvez/company_app/issues/23) | ✅ done |
| CR-041 | [#24](https://github.com/jsongalvez/company_app/issues/24) |
| CR-042 | [#25](https://github.com/jsongalvez/company_app/issues/25) | ✅ done |
| CR-043 | [#26](https://github.com/jsongalvez/company_app/issues/26) |
| CR-046 | [#27](https://github.com/jsongalvez/company_app/issues/27) |
| CR-047 | [#28](https://github.com/jsongalvez/company_app/issues/28) |
| CR-049 | [#29](https://github.com/jsongalvez/company_app/issues/29) |
| CR-050 | [#30](https://github.com/jsongalvez/company_app/issues/30) |
| CR-051 | [#31](https://github.com/jsongalvez/company_app/issues/31) |
| CR-052 | [#32](https://github.com/jsongalvez/company_app/issues/32) |
| CR-053 | [#33](https://github.com/jsongalvez/company_app/issues/33) |
| CR-054 | [#34](https://github.com/jsongalvez/company_app/issues/34) |

## To mark done

Append `\n**Status:** ✅ done` to the bottom of the ticket file, then check `[x]` above.
