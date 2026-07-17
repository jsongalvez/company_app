# Shell lifecycle module pattern

Extracted the shared app-boot lifecycle (build, start, port-wait, health-check, cleanup) into `scripts/lib/start-app.sh`, sourced by both `pre-commit` and `pre-push`. Two key design decisions:

**Caller-owned LOG_TAG.** The spec suggested the module log with a `[start-app]` tag. We chose to have callers set `LOG_TAG` before sourcing, so log lines inherit the hook's identity (`[pre-commit]`, `[pre-push]`). This makes logs unambiguous when reading a single hook's output — the module's origin is obvious from the sourcing line, and each log line tells you *which hook* ran it.

**Global APP_PID state.** `app_start` sets `APP_PID` as a global; `app_wait_ready` and `app_cleanup` read it. The alternative was returning the PID and passing it explicitly, but shell functions can only return one value. Passing PID as an argument to every function would add boilerplate at every call site. The global is idiomatic for shell and the implicit contract is documented in the module header.
