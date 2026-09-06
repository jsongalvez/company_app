# Slow-query visibility + EXPLAIN runbook (#474)

When the RED metrics say "slow query" rather than "saturated pool" (map #470
wolf-fence order: metrics halve first, then logs pinpoint, then this page
attributes), this page takes a slow search from symptom to cause in one pass:
plan vs data vs pool.

## 1. One-pass triage

| Signal | Reading | Next step |
|---|---|---|
| `PoolSaturated` firing (active == 3 with waiters) while SQL below is fast | Connection leak / pool too small, not slow queries | #473 dashboard, fix the leak |
| Top-slow below shows the search/inventory query with high mean time | Slow query | §3 EXPLAIN recipe |
| EXPLAIN shows the right index but high buffers/rows | Data shape (too many rows scanned) | §4 data leg |
| EXPLAIN shows Seq Scan where an index exists | Plan shape | §4 plan leg |

## 2. Setup (once per Postgres)

Tracking and slow-statement logging are server settings, not app code. The
agreed threshold is **500ms** (k6 `branches_latency` p95 contract; the
`ClientSearchSlow` alert pages at 1s, so 500ms catches both shapes early).

- **Local dev** — already in `docker/docker-compose.yml` (`command:` on the
  postgres service): `shared_preload_libraries=pg_stat_statements`,
  `pg_stat_statements.track=all`, `log_min_duration_statement=500`,
  `log_parameter_max_length=0`. Recreate after pulling:
  `docker compose -f docker/docker-compose.yml up -d`.
- **Coolify Postgres** — same four flags as the service's custom
  `command:` (or `postgresql.conf` override), then restart the database
  service. `shared_preload_libraries` is postmaster-context: setting it
  without a restart changes nothing. Verify after restart:
  `SHOW shared_preload_libraries;` must list `pg_stat_statements`.
- **CI test DB** — `.github/workflows/quality.yml` applies the same via
  `ALTER SYSTEM` + container restart before the Gradle run.

`log_parameter_max_length=0` needs PG16+ (local and CI run PG18). It masks
bound values in server duration logs. `pg_stat_statements.query` never holds
values at any version: it stores normalized text with `$n` placeholders.

## 3. Top-slow statements

After reproducing the slow search, rank by mean time (statement, mean, calls):

```sql
SELECT query, calls,
       mean_exec_time AS mean_ms,
       max_exec_time AS max_ms
FROM pg_stat_statements
WHERE query ILIKE '%client%' OR query ILIKE '%branch_inventory%'
ORDER BY mean_exec_time DESC
LIMIT 20;
```

Same read from code (DSL over the view, no raw SQL):
`SlowQueryRepository.topSlow()` in
`backend/src/main/kotlin/com/companyb/companyapp/observability/SlowQueryRepository.kt`.

## 4. EXPLAIN recipes

Copy the normalized text from §3, substitute a representative literal for each
`$n`, and run with analyze + buffers (never on prod with `ANALYZE` on a
write — these two reads are safe):

```sql
EXPLAIN (ANALYZE, BUFFERS)
-- pasted query here
;
```

### Client search (`ClientRepository.search`)

Shape: per-token `(similarity(...) > 0.2 OR first_name ILIKE ... OR
last_name ILIKE ... OR middle_name ILIKE ... OR phone LIKE ...)` over
`client WHERE deleted_at IS NULL`, ordered by `similarity()`.

Healthy plan: `BitmapOr` over `Bitmap Index Scan on idx_client_trgm`
(the composite GIN index on
`first_name/middle_name/last_name`, V1 line 261), then sort + limit 20.

- **Seq Scan on client instead** — check `pg_trgm` installed
  (`SELECT * FROM pg_extension WHERE extname = 'pg_trgm'`), the index exists
  (`\d client`), and the table was analyzed (`ANALYZE client;`). Small tables
  legitimately seq-scan: compare `actual rows` against the 20-row limit before
  calling it a problem.
- **Bitmap present but slow** — data leg: many rows match common trigrams
  (short 1–2 character tokens). That is working as designed; the 300ms
  frontend debounce plus 20-row limit bound it.

### Inventory fetch (`BranchInventoryRepository.findByBranch` / `findMovements`)

Shape: `branch_inventory ⨝ product` filtered on `branch_id` (+ `is_active`),
or `inventory_movement ⨝ branch_day ⨝ product` ordered by `moved_at DESC`.

Healthy plan: index scan on `branch_inventory` via the
`UNIQUE (branch_id, product_id)` constraint index, nested loop into `product`
by PK, filter `is_active`.

- **Seq Scan on product** — `is_active` is unselective (almost everything
  active); the fix is never an index on `is_active` alone. Check row counts
  first.
- **Slow movement history** — the `moved_at DESC` ordering sorts per branch;
  watch for an on-disk sort (`Sort Method: external merge`) on large
  branches.

## 5. PII rules

- `pg_stat_statements` output is safe to paste into tickets: parameters are
  `$n`, never values.
- Postgres duration logs are masked server-side (`log_parameter_max_length=0`).
- `EXPLAIN` output embeds the literal you substituted: run it against the
  local/dev database with a fake name, and never paste a real client name,
  phone number, or address into a ticket or dashboard annotation.
