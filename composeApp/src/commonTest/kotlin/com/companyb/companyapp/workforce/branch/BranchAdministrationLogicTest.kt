package com.companyb.companyapp.workforce.branch

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.dto.BranchResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BranchAdministrationLogicTest {
    @Test
    fun branchNameError_requires_non_blank_name() {
        assertEquals("Branch name is required", branchNameError("  "))
        assertNull(branchNameError(" Main Branch "))
    }

    @Test
    fun branchNameError_rejects_duplicate_name_and_type() {
        val existing = listOf(BranchResponse("b1", "Main Branch", BranchType.CLINIC))

        assertEquals(
            "A branch with this name and type already exists",
            branchNameError(" Main Branch ", BranchType.CLINIC, existing),
        )
        assertNull(branchNameError("Main Branch", BranchType.MEDICAL_MISSION, existing))
    }
}
