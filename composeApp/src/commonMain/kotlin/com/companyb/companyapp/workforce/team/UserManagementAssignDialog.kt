package com.companyb.companyapp.workforce.team

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.workforce.branch.AssignUserDialog
import com.companyb.companyapp.workforce.branch.AssignmentDialogActions
import com.companyb.companyapp.workforce.branch.BranchViewModel
import com.companyb.companyapp.workforce.team.UserViewModel

/**
 * #482 — assign-dialog pair gate: `showAssignUserDialog` + `assignmentBranch` render as one
 * pair. Closed or held-branch-missing → null (never render). A null loaded list means the
 * branch flow hasn't succeeded yet (first open, rotation/process-death restore mid-reload) —
 * keep the held snapshot so a legitimate open isn't killed by the reload. Over a loaded list
 * the held id re-resolves to the freshest row (a rename elsewhere shows the fresh name); a
 * held id absent from the list is a deleted branch → null so no ghost dialog renders.
 */
internal fun resolveAssignmentDialogBranch(
    showDialog: Boolean,
    heldBranch: BranchResponse?,
    loadedBranches: List<BranchResponse>?,
): BranchResponse? =
    when {
        !showDialog || heldBranch == null -> null
        loadedBranches == null -> heldBranch
        else -> loadedBranches.firstOrNull { it.id == heldBranch.id }
    }

/**
 * #482 — assign-dialog pair host, split out of [UserManagementBranchDialogs] for the #462
 * LongMethod burn-down (same budget split that produced UserManagementDialogHosts): the
 * dialog flag + held branch open, populate, and dismiss as one coherent pair, and the gate
 * helper lives here with its only consumer (BranchAdministration.kt sits at the
 * TooManyFunctions wall). 5 params so it stays LongParameterList-clean.
 */
@Composable
internal fun UserManagementAssignDialog(
    viewModel: UserViewModel,
    branchViewModel: BranchViewModel,
    mutationsDisabled: Boolean,
    assignmentResult: UiState<AssignmentResponse>,
    branchActions: UserManagementBranchDialogsActions,
) {
    val branchesState = viewModel.branches.collectAsState().value
    val loadedBranches = (branchesState as? UiState.Success<List<BranchResponse>>)?.data
    val assignmentDialogBranch =
        resolveAssignmentDialogBranch(
            branchActions.showAssignUserDialog,
            branchActions.assignmentBranch,
            loadedBranches,
        )
    // Heal a desynced pair (rotation/process-death restore landing on flag-true plus an
    // unresolvable branch, or a branch deleted elsewhere): converge to fully closed. Gated
    // on a loaded list so a legitimate restore isn't killed mid-reload; the render below
    // can never show a stale/missing branch.
    val pairOrphaned =
        branchActions.showAssignUserDialog && loadedBranches != null && assignmentDialogBranch == null
    LaunchedEffect(pairOrphaned) {
        if (pairOrphaned) {
            logInfo("UserManagementScreen", "assign pair orphaned without a resolvable branch; closing")
            branchActions.onAssignDialog(false)
            branchActions.onAssignmentBranchChange(null)
        }
    }
    if (assignmentDialogBranch != null) {
        val loadedUsers =
            viewModel.freshestUsers
                .collectAsState()
                .value
                .orEmpty()
        AssignUserDialog(
            branch = assignmentDialogBranch,
            users = loadedUsers,
            state = assignmentResult,
            mutationsDisabled = mutationsDisabled,
            actions =
                AssignmentDialogActions(
                    onAssign = { request ->
                        branchViewModel.createAssignment(assignmentDialogBranch.id, request)
                    },
                    onDismiss = {
                        branchViewModel.resetAdministrationState()
                        branchActions.onAssignDialog(false)
                        branchActions.onAssignmentBranchChange(null)
                    },
                ),
        )
    }
}
