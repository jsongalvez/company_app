# Shell lifecycle module pattern

Extracted the shared app-boot lifecycle (build, start, port-wait, health-check, cleanup) into `scripts/lib/start-app.sh`, originally sourced by both `pre-commit` and `pre-push`. Under the #329 throughput map, git hooks no longer start Gradle, the backend, or Postgres, so the hooks no longer source this module; it remains the shared lifecycle for manual k6/integration workflows. Two key design decisions:

**Caller-owned LOG_TAG.** The spec suggested the module log with a `[start-app]` tag. We chose to have callers set `LOG_TAG` before sourcing, so log lines inherit the caller's identity (`[pre-push]`, a k6 run). This makes logs unambiguous when reading a single caller's output — the module's origin is obvious from the sourcing line, and each log line tells you *which caller* ran it.

**Global APP_PID state.** `app_start` sets `APP_PID` as a global; `app_wait_ready` and `app_cleanup` read it. The alternative was returning the PID and passing it explicitly, but shell functions can only return one value. Passing PID as an argument to every function would add boilerplate at every call site. The global is idiomatic for shell and the implicit contract is documented in the module header.

**2026-08-21 (#330, map #329):** hooks are near-zero-cost bookkeeping and no longer source this module; the retired hook wiring ran `installDist` + boot + health-wait on every gate-sensitive push.
