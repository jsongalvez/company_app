package com.companyb.companyapp.app

import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #156 — the #92 locked capability context model replaces the ADR-0021 two-slice filters
 * (globalCapabilities / capabilitiesForBranch are gone). Resolution happens at consumption
 * sites: exact-triple checks (branch-scoped against selectedBranchId, day-scoped against a
 * day row's branchDayId) + any-context membership for route gates.
 */
class AppSessionCapabilityMatcherTest {
    private fun row(
        code: String,
        contextType: CapabilityContextType,
        contextId: String = "00000000-0000-0000-0000-000000000000",
    ) = UserCapabilityResponse(
        capabilityCode = code,
        contextType = contextType,
        contextId = contextId,
        sourceType = CapabilitySourceType.MANUAL_OVERRIDE,
    )

    private val rows =
        listOf(
            row("MANAGE_USERS", CapabilityContextType.GLOBAL),
            row("ASSIGN_DELEGATE", CapabilityContextType.GLOBAL),
            row("SUBMIT_REMITTANCE", CapabilityContextType.BRANCH, "b1"),
            row("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH, "b1"),
            row("EDIT_PAST_DAY", CapabilityContextType.BRANCH, "b2"),
            row("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, "d1"),
            row("VOID_SESSION", CapabilityContextType.MEDICAL_MISSION, "m1"),
            row("VIEW_BRANCH_DATA", CapabilityContextType.PROVINCIAL_TOUR, "t1"),
        )

    // ─────────────────────────── triple check ───────────────────────────

    @Test
    fun triple_check_matches_exact_code_contextType_contextId() {
        assertTrue(rows.hasCapability("SUBMIT_REMITTANCE", CapabilityContextType.BRANCH, "b1"))
        assertTrue(
            rows.hasCapability("MANAGE_USERS", CapabilityContextType.GLOBAL, "00000000-0000-0000-0000-000000000000"),
        )
        assertTrue(rows.hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, "d1"))
    }

    @Test
    fun triple_check_rejects_wrong_contextId() {
        assertFalse(rows.hasCapability("SUBMIT_REMITTANCE", CapabilityContextType.BRANCH, "b2"))
        assertFalse(
            rows.hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH, "b2"),
            "b1 row must not resolve for b2",
        )
    }

    @Test
    fun triple_check_rejects_wrong_contextType() {
        assertFalse(
            rows.hasCapability("MANAGE_USERS", CapabilityContextType.BRANCH, "00000000-0000-0000-0000-000000000000"),
        )
        assertFalse(rows.hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, "b1"))
    }

    @Test
    fun triple_check_rejects_wrong_code() {
        assertFalse(rows.hasCapability("VOID_SESSION", CapabilityContextType.BRANCH, "b1"))
    }

    @Test
    fun triple_check_null_contextId_fails_closed() {
        // Pre-clock-in (selectedBranchId null) and pre-day-selection (no branchDayId):
        // the null never matches, regardless of what rows are stored.
        assertFalse(rows.hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH, null))
        assertFalse(rows.hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, null))
    }

    @Test
    fun triple_check_empty_list_never_matches() {
        assertFalse(
            emptyList<UserCapabilityResponse>().hasCapability(
                "MANAGE_USERS",
                CapabilityContextType.GLOBAL,
                "00000000-0000-0000-0000-000000000000",
            ),
        )
    }

    @Test
    fun branch_scoped_resolution_shape_selected_branch() {
        // The Finance/dashboard call shape: resolve BRANCH rows against selectedBranchId.
        assertTrue(rows.hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH, "b1"))
        assertFalse(rows.hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH, "b2"))
        // BRANCH_DAY relief grant resolves only against the day row's branchDayId (#157-adjacent shape).
        assertTrue(rows.hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, "d1"))
        assertFalse(rows.hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY, "d2"))
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

    @Test
    fun anyContext_ignores_unknown_sentinel_rows() {
        // #876 — a newer server context coerces to UNKNOWN client-side; it must not
        // open any-context route gates (fail-closed), while known rows still grant.
        val unknownRows = rows + row("ASSIGN_COMPENSATION", CapabilityContextType.UNKNOWN)
        assertFalse(unknownRows.hasCapabilityAnyContext("ASSIGN_COMPENSATION"))
        assertTrue(unknownRows.hasCapabilityAnyContext("MANAGE_USERS"))
        assertFalse(
            unknownRows.hasCapabilityAtContextType("ASSIGN_COMPENSATION", CapabilityContextType.BRANCH),
            "UNKNOWN rows match no known context type",
        )
    }

    // ─────────────────────────── at-context-type (#158) ───────────────────────────

    @Test
    fun atContextType_matches_any_contextId_of_that_type() {
        assertTrue(rows.hasCapabilityAtContextType("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY))
        assertTrue(rows.hasCapabilityAtContextType("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH))
        assertTrue(rows.hasCapabilityAtContextType("MANAGE_USERS", CapabilityContextType.GLOBAL))
    }

    @Test
    fun atContextType_rejects_other_types_and_absent_codes() {
        assertFalse(rows.hasCapabilityAtContextType("EDIT_BRANCH_DATA", CapabilityContextType.MEDICAL_MISSION))
        assertFalse(rows.hasCapabilityAtContextType("ASSIGN_COMPENSATION", CapabilityContextType.BRANCH))
        assertFalse(rows.hasCapabilityAtContextType("MANAGE_USERS", CapabilityContextType.BRANCH))
        assertFalse(
            emptyList<UserCapabilityResponse>().hasCapabilityAtContextType(
                "EDIT_BRANCH_DATA",
                CapabilityContextType.BRANCH_DAY,
            ),
        )
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
