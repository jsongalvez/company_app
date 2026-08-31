package com.companyb.companyapp.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BranchAdministrationLogicTest {
    @Test
    fun branchNameError_requires_non_blank_name() {
        assertEquals("Branch name is required", branchNameError("  "))
        assertNull(branchNameError(" Main Branch "))
    }
}
