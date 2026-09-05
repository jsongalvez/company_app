# Incident report runbook (#475)

Triage-ready incident packets close the wolf-fence loop from map #470: a slow
request (or a 5xx) arrives with trace id, route, elapsed, app version,
timestamp, and pool snapshot already attached.

## Packet shape

`IncidentPacket` (`shared/.../dto/FeedbackDto.kt`), identical for user
reports (`USER_REPORT`) and 5xx auto-files (`AUTO_5XX`):

```
traceId, method, route, status, elapsedMs, appVersion,
timestamp, pool{active,idle,awaiting,total}, reporter, source
```

- `route` is normalized (`UUID`/numeric segments → `{id}`) with the query
  string stripped, so search terms never enter a packet.
- `reporter` is the masked caller id (`maskUUID`); `timestamp` is
  server-stamped. Packets carry no query, body, IP, or token.
- `status` is null on user reports (that request already completed);
  `elapsedMs` is client-measured there, server-measured on auto-files.

## Filing paths

1. **In-app report** — authenticated `POST /api/feedback`
   (`FeedbackRequest`: `traceId`, `endpoint` in `"METHOD /path"` shape,
   `appVersion`, optional `elapsedMs`). Client recipe reuses the #471 helper:
   `response.traceId()` + `BugReport(...).format()` supply the fields.
   Per-user and per-IP budgets share `RateLimiter` (10/min); over-budget
   answers 429. Repeat posts for one trace id return the first packet with
   `duplicate: true` — no second issue.
2. **5xx auto-file** — any response leaving as 500 files the same shape via
   the status handler (`Main.registerServerErrorHandler`), no user action.
   The 500 body stays generic (`{"error":"Internal Server Error"}`).

## Delivery

`IncidentDelivery` mirrors the #353 SMTP pattern: `GITHUB_TOKEN` plus
`GITHUB_REPOSITORY=owner/repo` set → async needs-triage GitHub issue
(`GithubIssueSender`, stdlib HTTP, bounded single-thread queue, failures
logged, request path never breaks). Unset → server-log relay: the packet is
one JSON log line, and the triage loop files the issue:

```bash
# find the packet for a cited trace id, correlate with the request logs
jq -c 'select(.traceId == "<trace>")' logs/app.log
jq -c 'select(.traceId == "<trace>" and .event == "request_completed")' logs/app.log

# file it (packet line becomes the body)
trace=<trace>
packet=$(jq -c "select(.traceId == \"$trace\" and .source != null)" logs/app.log | head -n 1)
route=$(echo "$packet" | jq -r '"\(.method) \(.route)"')
gh issue create --label needs-triage \
  --title "Incident ${trace:0:8} $route" \
  --body "Auto-filed incident packet. Start the wolf-fence here: \`$route\`, pool vs plan vs data per docs/slow-query-runbook.md. Packet: $packet"
```

## Decisions (in-ticket)

- Bearer-only, no capability gate (clock-in/dashboard precedent): every
  signed-in user may report.
- No free-text note field: unstructured text is the PII vector.
- No `incident_report` table: the registry is last-1000 in-memory dedup plus
  the log relay / GitHub issue — a table would add migration, audit, and
  read-gate surface for a debugging read (same YAGNI call as #474's `topSlow`).
- Health 503 stays out: intentional degraded signal, not an incident.
- No composeApp change: the endpoint plus the existing
  `traceId()`/`BugReport` helper is the full client surface.
