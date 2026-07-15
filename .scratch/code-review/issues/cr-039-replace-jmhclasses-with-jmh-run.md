# CR-039: Replace jmhClasses with actual JMH run in pre-commit

**Source:** CR-036 audit follow-up (P1)

## What to build

Change `.githooks/pre-commit` step 2 from `:backend:jmhClasses` to run actual JMH benchmarks and verify baselines. `jmhClasses` only compiles benchmark code — it gives zero regression protection. Replace it with `:backend:jmh` followed by `check-baselines.sh`, so a >20% performance regression blocks the commit.

## Acceptance criteria

- [ ] `.githooks/pre-commit` step 2 replaces `:backend:jmhClasses` with `./gradlew :backend:jmh --no-daemon`
- [ ] JMH output is piped to `/tmp/company-app-jmh-precommit.log`
- [ ] After JMH succeeds, `bash scripts/check-baselines.sh /tmp/company-app-jmh-precommit.log` is called
- [ ] If baselines regressed >20%, commit is blocked with a clear error message pointing to the log file
- [ ] Full JMH run time impact is documented (expected: ~30-60s additional pre-commit latency)

## Blocked by

None — can start immediately.

## Status: ready-for-agent
