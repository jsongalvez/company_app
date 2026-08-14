package com.companyb.companyapp.state

import com.companyb.companyapp.dto.UserCapabilityResponse
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #156 — the #92 locked capability context model replaces the ADR-0021 two-slice filters
 * (globalCapabilities / capabilitiesForBranch are gone). Resolution happens at consumption
 * sites: exact-triple checks (branch-scoped against selectedBranchId, day-scoped against a
 * day row's branchDayId) + any-context membership for route gates.
 */
class SessionCapabilityMatcherTest {
    private fun row(
        code: String,
        contextType: String,
        contextId: String = "00000000-0000-0000-0000-000000000000",
    ) = UserCapabilityResponse(
        capabilityCode = code,
        contextType = contextType,
        contextId = contextId,
        sourceType = "DIRECT",
    )

    private val rows =
        listOf(
            row("MANAGE_USERS", "GLOBAL"),
            row("ASSIGN_DELEGATE", "GLOBAL"),
            row("SUBMIT_REMITTANCE", "BRANCH", "b1"),
            row("EDIT_BRANCH_DATA", "BRANCH", "b1"),
            row("EDIT_PAST_DAY", "BRANCH", "b2"),
            row("EDIT_BRANCH_DATA", "BRANCH_DAY", "d1"),
            row("VOID_SESSION", "MEDICAL_MISSION", "m1"),
            row("VIEW_BRANCH_DATA", "PROVINCIAL_TOUR", "t1"),
        )

    // ─────────────────────────── triple check ───────────────────────────

    @Test
    fun triple_check_matches_exact_code_contextType_contextId() {
        assertTrue(rows.hasCapability("SUBMIT_REMITTANCE", "BRANCH", "b1"))
        assertTrue(rows.hasCapability("MANAGE_USERS", "GLOBAL", "00000000-0000-0000-0000-000000000000"))
        assertTrue(rows.hasCapability("EDIT_BRANCH_DATA", "BRANCH_DAY", "d1"))
    }

    @Test
    fun triple_check_rejects_wrong_contextId() {
        assertFalse(rows.hasCapability("SUBMIT_REMITTANCE", "BRANCH", "b2"))
        assertFalse(rows.hasCapability("EDIT_BRANCH_DATA", "BRANCH", "b2"), "b1 row must not resolve for b2")
    }

    @Test
    fun triple_check_rejects_wrong_contextType() {
        assertFalse(rows.hasCapability("MANAGE_USERS", "BRANCH", "00000000-0000-0000-0000-000000000000"))
        assertFalse(rows.hasCapability("EDIT_BRANCH_DATA", "BRANCH_DAY", "b1"))
    }

    @Test
    fun triple_check_rejects_wrong_code() {
        assertFalse(rows.hasCapability("VOID_SESSION", "BRANCH", "b1"))
    }

    @Test
    fun triple_check_null_contextId_fails_closed() {
        // Pre-clock-in (selectedBranchId null) and pre-day-selection (no branchDayId):
        // the null never matches, regardless of what rows are stored.
        assertFalse(rows.hasCapability("EDIT_BRANCH_DATA", "BRANCH", null))
        assertFalse(rows.hasCapability("EDIT_BRANCH_DATA", "BRANCH_DAY", null))
    }

    @Test
    fun triple_check_empty_list_never_matches() {
        assertFalse(
            emptyList<UserCapabilityResponse>().hasCapability(
                "MANAGE_USERS",
                "GLOBAL",
                "00000000-0000-0000-0000-000000000000",
            ),
        )
    }

    @Test
    fun branch_scoped_resolution_shape_selected_branch() {
        // The Finance/dashboard call shape: resolve BRANCH rows against selectedBranchId.
        assertTrue(rows.hasCapability("EDIT_BRANCH_DATA", "BRANCH", "b1"))
        assertFalse(rows.hasCapability("EDIT_BRANCH_DATA", "BRANCH", "b2"))
        // BRANCH_DAY relief grant resolves only against the day row's branchDayId (#157-adjacent shape).
        assertTrue(rows.hasCapability("EDIT_BRANCH_DATA", "BRANCH_DAY", "d1"))
        assertFalse(rows.hasCapability("EDIT_BRANCH_DATA", "BRANCH_DAY", "d2"))
    }

    // ─────────────────────────── any-context ───────────────────────────

    @Test
    fun anyContext_matches_across_all_contexts() {
        assertTrue(rows.hasCapabilityAnyContext("MANAGE_USERS"))
        assertTrue(rows.hasCapabilityAnyContext("SUBMIT_REMITTANCE"))
        assertTrue(rows.hasCapabilityAnyContext("EDIT_BRANCH_DATA"), "BRANCH and BRANCH_DAY rows both count")
        assertTrue(rows.hasCapabilityAnyContext("VIEW_BRANCH_DATA"))
    }

    @Test
    fun anyContext_misses_absent_code() {
        assertFalse(rows.hasCapabilityAnyContext("ASSIGN_COMPENSATION"))
        assertFalse(emptyList<UserCapabilityResponse>().hasCapabilityAnyContext("MANAGE_USERS"))
    }

    // ─────────────────────────── at-context-type (#158) ───────────────────────────

    @Test
    fun atContextType_matches_any_contextId_of_that_type() {
        assertTrue(rows.hasCapabilityAtContextType("EDIT_BRANCH_DATA", "BRANCH_DAY"))
        assertTrue(rows.hasCapabilityAtContextType("EDIT_BRANCH_DATA", "BRANCH"))
        assertTrue(rows.hasCapabilityAtContextType("MANAGE_USERS", "GLOBAL"))
    }

    @Test
    fun atContextType_rejects_other_types_and_absent_codes() {
        assertFalse(rows.hasCapabilityAtContextType("EDIT_BRANCH_DATA", "MEDICAL_MISSION"))
        assertFalse(rows.hasCapabilityAtContextType("ASSIGN_COMPENSATION", "BRANCH"))
        assertFalse(rows.hasCapabilityAtContextType("MANAGE_USERS", "BRANCH"))
        assertFalse(emptyList<UserCapabilityResponse>().hasCapabilityAtContextType("EDIT_BRANCH_DATA", "BRANCH_DAY"))
    }

    @Test
    fun hasDayGrant_matches_only_branchDay_rows() {
        assertTrue(rows.hasDayGrant("EDIT_BRANCH_DATA"))
        assertFalse(rows.hasDayGrant("MANAGE_USERS"), "GLOBAL rows are not day grants")
        assertFalse(rows.hasDayGrant("ASSIGN_COMPENSATION"), "absent code never matches")
        assertFalse(emptyList<UserCapabilityResponse>().hasDayGrant("EDIT_BRANCH_DATA"))
    }

    @Test
    fun branchOrDay_matches_branch_leg_or_day_leg() {
        // BRANCH leg: the b1 row.
        assertTrue(rows.hasBranchOrDayCapability("EDIT_BRANCH_DATA", "b1", null))
        // Day leg: the d1 row (null branch fails its leg closed but the day leg carries).
        assertTrue(rows.hasBranchOrDayCapability("EDIT_BRANCH_DATA", null, "d1"))
        // Both null / wrong ids fail closed.
        assertFalse(rows.hasBranchOrDayCapability("EDIT_BRANCH_DATA", null, null))
        assertFalse(rows.hasBranchOrDayCapability("EDIT_BRANCH_DATA", "b2", "d2"))
        // The day leg never serves a branch id (a BRANCH id is not a day id).
        assertFalse(rows.hasBranchOrDayCapability("EDIT_BRANCH_DATA", "b2", "b1"))
    }
}
