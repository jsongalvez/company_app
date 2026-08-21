## Question

How should backend lifecycle own Hikari shutdown? Add idempotent datasource close handling to server-stop and startup-failure paths without closing while requests are active, and prove same-process restart does not retain pool threads or stale connections.
