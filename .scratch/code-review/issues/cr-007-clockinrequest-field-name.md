# CR-007: ClockInRequest field name mismatch (attendanceId vs id)

**Source:** Spec review of US-001–010 (chunk 1)

**What:** US-006 spec says request body has `{ attendanceId, branchId }` but the `ClockInRequest` data class uses the field name `id` instead of `attendanceId`. This is a documentation mismatch or a field naming inconsistency.

**Spec reference:** US-006: "Expose POST /attendance/clock-in body: { attendanceId, branchId }"

**Fix:** Either rename the field in `ClockInRequest` to `attendanceId` (with `@SerialName` if needed) or update the spec documentation.

**Priority:** low
**Story alignment:** US-006

**Status:** ✅ done
