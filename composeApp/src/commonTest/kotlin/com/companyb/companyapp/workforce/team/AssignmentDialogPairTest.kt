package com.companyb.companyapp.workforce.team

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.dto.BranchResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Tests for the #482 assign-dialog pair gate ([resolveAssignmentDialogBranch]):
 * `showAssignUserDialog` + `assignmentBranch` live and die together — no dialog with a
 * stale/missing branch, no retained branch after dismiss, no orphaned flag across
 * rotation/process-death restores.
 */
class AssignmentDialogPairTest {
    private val held = BranchResponse(id = "b1", name = "Makati", branchType = BranchType.CLINIC)
    private val fresh =
        BranchResponse(id = "b1", name = "Makati Renamed", branchType = BranchType.CLINIC)
    private val other = BranchResponse(id = "b2", name = "Cebu", branchType = BranchType.CLINIC)

    @Test
    fun closed_pair_never_renders() {
        assertNull(resolveAssignmentDialogBranch(false, held, listOf(held)))
        assertNull(resolveAssignmentDialogBranch(false, null, listOf(held)))
        assertNull(resolveAssignmentDialogBranch(false, held, null))
    }

    @Test
    fun open_pair_without_held_branch_never_renders() {
        assertNull(resolveAssignmentDialogBranch(true, null, listOf(held)))
        assertNull(resolveAssignmentDialogBranch(true, null, null))
    }

    @Test
    fun open_pair_before_branch_load_keeps_held_snapshot() {
        assertSame(held, resolveAssignmentDialogBranch(true, held, null))
    }

    @Test
    fun open_pair_resolves_freshest_row_for_held_id() {
        assertEquals(fresh, resolveAssignmentDialogBranch(true, held, listOf(other, fresh)))
    }

    @Test
    fun open_pair_with_deleted_branch_resolves_to_null() {
        assertNull(resolveAssignmentDialogBranch(true, held, listOf(other)))
        assertNull(resolveAssignmentDialogBranch(true, held, emptyList()))
    }
}
