# ADR-0009: Rate gap prevention by construction

**Status:** Accepted  
**Date:** 2026-07-17  
**Driver:** CR-019 (issue #10)  
**Tags:** session-base-rate, gap-detection, exclusion-constraint

## Context

Session base rates have `effective_from` / `effective_until` columns. When a new rate is set for the same `(branch_id, session_type)`, the previous rate must be deactivated and a new one inserted. The constraint `no_rate_overlap` (GiST exclusion on branch_id + session_type + tstzrange) prevents overlapping ranges — but it does not prevent **gaps** between rates (periods with no active rate for a branch+type).

The original spec (CR-019) asked for gap *detection*: validate that `new.effective_from == old.effective_until + 1 microsecond` and reject with 400 if a gap would form.

## Decision

Prevent gaps **by construction** instead of detecting and rejecting them:

1. **Single timestamp capture:** Capture `now = OffsetDateTime.now(ZoneOffset.UTC)` once at the start of `SessionBaseRateService.setRate()`.
2. **Same timestamp for both operations:** Pass the same `now` to both `deactivatePreviousRates()` (which sets `effective_until = now` on matching rates) and `setRate()` (which sets `effective_from = now` on the new rate).
3. **Half-open exclusion constraint:** Changed the GiST exclusion constraint from `tstzrange(effective_from, effective_until, '[]')` to `'[)'` so the deactivated range `[old_from, now)` and the new range `[now, far_future)` do not overlap at the boundary.

## Consequences

### Positive
- **No TOCTOU race.** The timestamp is captured once; the deactivation and insertion use the same value. There is no window where another caller could insert a rate between the two operations.
- **No dead validation code.** A validation check that can never fire (since `effective_from` is always the same `now`) would be dead code. Prevention eliminates the need for it.
- **Simpler contract.** Callers do not need to understand microsecond arithmetic or gap semantics. They just call `setRate()` and get seamless coverage.

### Negative
- **Deviates from spec wording.** The issue explicitly asked for a 400-rejection path. Prevention achieves the same goal through a different mechanism.
- **Half-open constraint is a schema change.** The V8 migration alters the constraint, which must be applied to existing databases.

## Alternatives considered

### Gap detection with validation
Check if `new.effective_from == old.effective_until + 1 microsecond` and throw `BadRequestResponse` if not. Rejected because: the validation would always pass (since `effective_from` is always `now`), making it dead code; and it introduces a TOCTOU window between the check and the insert.

### Gap detection at the storage layer
Use a `BEFORE INSERT` trigger that checks for gaps. Rejected because the project prohibits raw SQL in favour of the Exposed DSL and service-layer logic.
