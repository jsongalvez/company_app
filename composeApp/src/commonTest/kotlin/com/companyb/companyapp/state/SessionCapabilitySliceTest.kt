package com.companyb.companyapp.state

import com.companyb.companyapp.dto.UserCapabilityResponse
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #94-grad — the ADR-0021 two-slice capability filters: GLOBAL-only before a branch is
 * selected, GLOBAL + the selected branch's BRANCH rows after clock-in. BRANCH_DAY /
 * MEDICAL_MISSION / PROVINCIAL_TOUR rows are excluded by design (the code-only `Set<String>`
 * cannot represent them — the #99 F7 full context model is its own fog decision).
 */
class SessionCapabilitySliceTest {
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
            row("VOID_SESSION", "BRANCH_DAY", "d1"),
            row("MANAGE_PRODUCTS", "MEDICAL_MISSION", "m1"),
            row("VIEW_BRANCH_DATA", "PROVINCIAL_TOUR", "t1"),
        )

    @Test
    fun globalSlice_keeps_only_global_context_rows() {
        assertEquals(setOf("MANAGE_USERS", "ASSIGN_DELEGATE"), globalCapabilities(rows))
    }

    @Test
    fun globalSlice_empty_input_empty_set() {
        assertEquals(emptySet<String>(), globalCapabilities(emptyList()))
    }

    @Test
    fun branchSlice_keeps_global_plus_matching_branch_only() {
        assertEquals(
            setOf("MANAGE_USERS", "ASSIGN_DELEGATE", "SUBMIT_REMITTANCE", "EDIT_BRANCH_DATA"),
            capabilitiesForBranch(rows, "b1"),
        )
    }

    @Test
    fun branchSlice_excludes_other_branch_and_non_branch_contexts() {
        val slice = capabilitiesForBranch(rows, "b2")
        assertEquals(setOf("MANAGE_USERS", "ASSIGN_DELEGATE", "EDIT_PAST_DAY"), slice)
        assertEquals(false, "SUBMIT_REMITTANCE" in slice)
        assertEquals(false, "VOID_SESSION" in slice)
    }
}
