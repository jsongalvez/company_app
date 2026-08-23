package com.companyb.companyapp.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for the #383 row-context pure helpers: [auditBranchDisplayName] (branch rows show the
 * human name; branchless rows show an explicit marker — never a raw UUID or blank) and
 * [auditDateRangeErrors] (validate-on-apply, per-field errors; blank = no filter).
 */
class AuditLogBranchContextTest {
    @Test
    fun branch_row_shows_resolved_name() {
        assertEquals("Makati Branch", auditBranchDisplayName("Makati Branch"))
    }

    @Test
    fun branchless_row_shows_explicit_marker() {
        assertEquals(AUDIT_NO_BRANCH_MARKER, auditBranchDisplayName(null))
    }

    @Test
    fun blank_branch_name_counts_as_branchless() {
        assertEquals(AUDIT_NO_BRANCH_MARKER, auditBranchDisplayName(""))
        assertEquals(AUDIT_NO_BRANCH_MARKER, auditBranchDisplayName("  "))
    }

    @Test
    fun valid_dates_produce_no_errors() {
        val (fromError, toError) = auditDateRangeErrors("2026-08-01", "2026-08-23")
        assertNull(fromError)
        assertNull(toError)
    }

    @Test
    fun blank_sides_are_absent_filters_not_errors() {
        val (fromError, toError) = auditDateRangeErrors("", "  ")
        assertNull(fromError)
        assertNull(toError)
    }

    @Test
    fun malformed_side_is_flagged_on_its_own_field() {
        val (fromError, toError) = auditDateRangeErrors("2026-8-1", "2026-08-23")
        assertEquals(AUDIT_DATE_FORMAT_ERROR, fromError)
        assertNull(toError)

        val (fromError2, toError2) = auditDateRangeErrors("2026-08-01", "nope")
        assertNull(fromError2)
        assertEquals(AUDIT_DATE_FORMAT_ERROR, toError2)
    }

    @Test
    fun inverted_range_flags_to() {
        val (fromError, toError) = auditDateRangeErrors("2026-08-23", "2026-08-01")
        assertNull(fromError)
        assertEquals(AUDIT_DATE_ORDER_ERROR, toError)
    }

    @Test
    fun equal_from_and_to_is_allowed() {
        val (fromError, toError) = auditDateRangeErrors("2026-08-01", "2026-08-01")
        assertNull(fromError)
        assertNull(toError)
    }

    @Test
    fun malformed_beats_ordering_on_the_same_field() {
        // A malformed To can't be ordered — only the format error shows.
        val (_, toError) = auditDateRangeErrors("2026-08-23", "bad")
        assertEquals(AUDIT_DATE_FORMAT_ERROR, toError)
    }
}
