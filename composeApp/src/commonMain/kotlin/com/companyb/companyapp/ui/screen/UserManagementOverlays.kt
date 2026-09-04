package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.viewmodel.BranchViewModel
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.UserViewModel

/**
 * Member + branch-admin dialog overlays hoisted out of [UserManagementScreen] for the #462
 * LongMethod burn-down. New-file split: UserManagementScreen.kt, UserManagementHeader.kt and
 * UserManagementDialogs.kt all sit at the detekt file-function wall (10/11), so no existing
 * file can host another helper. Self-sufficient host: collects the dialog flows itself
 * (duplicate StateFlow subscriptions are cheap — LoginNoticeEffect precedent) so the Screen
 * call site passes only targets + single-line setters; the assign-close multi-line body lives
 * here (call-site lambda bodies count toward the caller LongMethod).
 */
@Composable
internal fun UserManagementDialogHosts(
    viewModel: UserViewModel,
    branchViewModel: BranchViewModel,
    mutationsDisabled: Boolean,
    memberActions: UserManagementMemberDialogsActions,
    branchActions: UserManagementBranchDialogsActions,
) {
    val heldList by viewModel.freshestUsers.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val actionErrors by viewModel.actionErrors.collectAsState()
    val createBranchState by branchViewModel.createBranchState.collectAsState()
    val assignmentResult by branchViewModel.assignmentResult.collectAsState()
    val deleteAssignmentState by branchViewModel.deleteAssignmentState.collectAsState()

    UserManagementMemberDialogs(
        viewModel = viewModel,
        mutationsDisabled = mutationsDisabled,
        deactivateTarget = memberActions.deactivateTarget,
        onDismissDeactivate = memberActions.onDismissDeactivate,
        slotEditTarget = memberActions.slotEditTarget,
        actionErrors = actionErrors,
        onDismissSlotEdit = memberActions.onDismissSlotEdit,
        showCreateUserDialog = memberActions.showCreateUserDialog,
        onCloseCreateUser = memberActions.onCloseCreateUser,
        roleEditTarget = memberActions.roleEditTarget,
        onDismissRoleEdit = memberActions.onDismissRoleEdit,
    )

    UserManagementBranchDialogs(
        branchViewModel = branchViewModel,
        mutationsDisabled = mutationsDisabled,
        showCreateBranchDialog = branchActions.showCreateBranchDialog,
        createBranchState = createBranchState,
        loadedBranches = (branches as? UiState.Success<List<BranchResponse>>)?.data.orEmpty(),
        onCloseCreateBranch = branchActions.onCloseCreateBranch,
        showAssignUserDialog = branchActions.showAssignUserDialog,
        assignmentBranch = branchActions.assignmentBranch,
        loadedUsers = heldList.orEmpty(),
        assignmentResult = assignmentResult,
        onCloseAssign = {
            branchActions.onAssignDialog(false)
            branchActions.onAssignmentBranchChange(null)
        },
        removeAssignmentTarget = branchActions.removeAssignmentTarget,
        deleteAssignmentState = deleteAssignmentState,
        onClearRemoveTarget = branchActions.onClearRemoveTarget,
    )
}

/**
 * Top-sections actions construction hoisted out of [UserManagementScreen] for the #462
 * LongMethod burn-down. Lives here (not Header.kt) because Header.kt sits at the detekt
 * file-function wall (10/11) — Overlays.kt has fresh budget. Owns the derivations plus
 * the multi-line refresh/create/assign bodies; the Screen passes single-line setters,
 * method refs, and raw dialog states via [UserManagementTopSectionsCallbacks]
 * (call-site lambda bodies count toward the caller's LongMethod). Plain fun with 4
 * params so it stays LongParameterList-clean outside the LPL-excluded Screen file.
 */
internal fun userManagementTopSectionsActions(
    users: UiState<List<UserSummaryResponse>>,
    heldNonNull: Boolean,
    mutationsDisabled: Boolean,
    callbacks: UserManagementTopSectionsCallbacks,
): UserManagementTopSectionsActions =
    UserManagementTopSectionsActions(
        // Typing against an Error state with nothing held does nothing visible (ErrorCard
        // renders instead of the list) — disable so the field doesn't look interactive
        // (pass-1 P4 SOFT). With held rows the keep-last gate renders the list, so the
        // client-side filter stays live over the mirror (#161).
        searchEnabled = heldNonNull || users !is UiState.Error,
        onSearchChange = callbacks.onSearchChange,
        onInvite = { callbacks.onShowCreateUser(true) },
        onRefresh = {
            callbacks.onLoadUsers()
            callbacks.onLoadBranches()
        },
        // Gated on a rendered list too: with nothing held (failed initial load) an
        // appended created row would be invisible behind the ErrorCard — force the
        // retry path instead (pass-4 P4).
        inviteEnabled = !mutationsDisabled && heldNonNull,
        refreshEnabled = !mutationsDisabled,
        onBranchSelected = callbacks.onBranchSelected,
        onRetryBranches = callbacks.onLoadBranches,
        onCreateBranch = {
            callbacks.onResetAdministration()
            callbacks.onShowCreateBranch(true)
        },
        onAssign = {
            callbacks.onResetAdministration()
            callbacks.onAssignmentBranchChange(callbacks.selectedBranch)
            callbacks.onShowAssignDialog(true)
        },
        pickerDisabled = mutationsDisabled,
        createEnabled = !mutationsDisabled,
        assignEnabled = !mutationsDisabled && heldNonNull,
        assignmentResult = callbacks.assignmentResult,
        removeAssignmentTarget = callbacks.removeAssignmentTarget,
        showAssignDialog = callbacks.showAssignDialog,
    )
