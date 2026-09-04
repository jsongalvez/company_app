package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
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
 * User-list region (status-when + LazyColumn) hoisted out of [UserManagementScreen] for the
 * #462 LongMethod burn-down. Lives here (not Header.kt) because Header.kt sits at the detekt
 * file-function wall (10/11) — Overlays.kt has fresh budget. Owns the slot-order actions
 * construction (the multi-line swap lambda) plus the error/empty/items branches; the Screen
 * passes derivations + single-line setters via [UserManagementUserListActions] (call-site
 * lambda bodies count toward the caller's LongMethod). ColumnScope receiver so the LazyColumn
 * keeps its `weight` (ColumnScope-bound member extension — same reason the LazyItemScope
 * `item {}` wrappers and `fillParentMaxSize` live inside wherever their scope resolves).
 * 5 params so it stays LongParameterList-clean outside the LPL-excluded Screen file.
 */
@Composable
internal fun ColumnScope.UserManagementUserList(
    users: UiState<List<UserSummaryResponse>>,
    heldNonNull: Boolean,
    filteredUsers: List<UserSummaryResponse>,
    selectedBranch: BranchResponse?,
    actions: UserManagementUserListActions,
) {
    if (!heldNonNull) {
        UserManagementLoadFallback(
            users = users,
            onRetry = actions.onRetry,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (selectedBranch != null) {
            item(key = "slot-order") {
                UserManagementSlotOrderItem(
                    selectedBranchId = selectedBranch.id,
                    branchName = actions.selectedBranchName,
                    rows = actions.slotRows,
                    mutationsDisabled = actions.mutationsDisabled,
                    actions =
                        UserManagementSlotOrderActions(
                            actionErrors = actions.actionErrors,
                            onSwap = { a, b -> actions.onSwapSlots(selectedBranch.id, a, b) },
                            onEditSlot = actions.onEditSlotTarget,
                        ),
                )
            }
        }

        item(key = "users-header") {
            UserManagementListHeader()
        }

        if (users is UiState.Error) {
            val errorState = users as UiState.Error
            item(key = "users-reload-error") {
                UserManagementListErrorRow(
                    message = errorState.message,
                    retryEnabled = !actions.mutationsDisabled,
                    onRetry = actions.onRetry,
                )
            }
        }

        if (filteredUsers.isEmpty()) {
            item(key = "users-empty") {
                Box(Modifier.fillParentMaxSize()) {
                    UserManagementEmptyContent(searchQuery = actions.searchQuery)
                }
            }
        } else {
            items(filteredUsers, key = { it.id }) { user ->
                UserManagementUserRowHost(
                    user = user,
                    expanded = user.id in actions.expandedIds,
                    actions = actions.userRowActions,
                )
            }
        }
    }
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

/**
 * Mutations-disabled gate hoisted out of [UserManagementScreen] for the #462 LongMethod
 * burn-down. Lives here (not Header.kt) because Header.kt sits at the detekt file-function
 * wall (10/11) — Overlays.kt has fresh budget. Self-sufficient: collects the in-flight plus
 * the five load flows itself (duplicate StateFlow subscriptions are cheap —
 * LoginNoticeEffect precedent) so the Screen drops three single-use collects
 * (`inFlight`/`createBranchState`/`deleteAssignmentState` fed only this gate) and keeps one
 * slim call; 2 params so it stays LongParameterList-clean outside the LPL-excluded Screen
 * file. Value-returning @Composable (the gate must `collectAsState` — `.value` reads in
 * composition go stale).
 */
@Composable
internal fun userManagementMutationsDisabled(
    viewModel: UserViewModel,
    branchViewModel: BranchViewModel,
): Boolean {
    val inFlight by viewModel.inFlight.collectAsState()
    val users by viewModel.users.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val createBranchState by branchViewModel.createBranchState.collectAsState()
    val assignmentResult by branchViewModel.assignmentResult.collectAsState()
    val deleteAssignmentState by branchViewModel.deleteAssignmentState.collectAsState()
    // Mutations disabled while one is in flight (ADR-0022) OR while a reload is in flight: the
    // keep-last gate renders live rows during Loading, and a mutation landing mid-load would be
    // clobbered by the load's pre-mutation snapshot (pass-1 HARD — the VM guard covers the
    // same-frame tap; this gate is the visible affordance).
    return inFlight.isNotEmpty() ||
        users is UiState.Loading ||
        branches is UiState.Loading ||
        createBranchState is UiState.Loading ||
        assignmentResult is UiState.Loading ||
        deleteAssignmentState is UiState.Loading
}

/**
 * First-composition entry loads hoisted out of [UserManagementScreen] for the #462
 * LongMethod burn-down. Lives here (not Header.kt) because Header.kt sits at the detekt
 * file-function wall (10/11) — Overlays.kt has fresh budget. Self-sufficient host: collects
 * the retained-VM reload gates itself (duplicate StateFlow subscriptions are cheap —
 * LoginNoticeEffect precedent) so the Screen keeps one slim call; 2 params so it stays
 * LongParameterList-clean outside the LPL-excluded Screen file.
 */
@Composable
internal fun UserManagementEntryEffects(
    viewModel: UserViewModel,
    branchViewModel: BranchViewModel,
) {
    val assignmentResult by branchViewModel.assignmentResult.collectAsState()
    val deleteAssignmentState by branchViewModel.deleteAssignmentState.collectAsState()
    val createBranchState by branchViewModel.createBranchState.collectAsState()
    LaunchedEffect(Unit) {
        logInfo("UserManagementScreen", "composable entered (first composition)")
        // Avoid starting a pre-mutation reload when a retained admin VM is re-entered. The
        // mutation's success effect owns the authoritative post-mutation reload.
        if (assignmentResult !is UiState.Loading && deleteAssignmentState !is UiState.Loading) {
            viewModel.loadUsers()
        }
        if (createBranchState !is UiState.Loading) {
            viewModel.loadBranches()
        }
        viewModel.loadRoles()
    }
}

/**
 * Post-mutation reload effects hoisted out of [UserManagementScreen] for the #462
 * LongMethod burn-down (companion: [UserManagementEntryEffects] for first-composition
 * loads). Lives here because UserManagementScreen.kt sits at the detekt file-function
 * wall (10/11) — Overlays.kt has fresh budget. Self-sufficient host: collects the three
 * branch-admin flows itself (duplicate StateFlow subscriptions are cheap —
 * LoginNoticeEffect precedent) so the Screen passes only the two VMs plus single-line
 * close setters; 5 params so it stays LongParameterList-clean outside the LPL-excluded
 * Screen file.
 */
@Composable
internal fun UserManagementMutationEffects(
    viewModel: UserViewModel,
    branchViewModel: BranchViewModel,
    onCloseCreateBranch: () -> Unit,
    onCloseAssign: () -> Unit,
    onClearRemoveTarget: () -> Unit,
) {
    val createBranchState by branchViewModel.createBranchState.collectAsState()
    val assignmentResult by branchViewModel.assignmentResult.collectAsState()
    val deleteAssignmentState by branchViewModel.deleteAssignmentState.collectAsState()

    LaunchedEffect(createBranchState) {
        when (val state = createBranchState) {
            is UiState.Success -> {
                logInfo("UserManagementScreen", "createBranchState=Success; reloading branches")
                viewModel.loadBranches()
                onCloseCreateBranch()
                branchViewModel.resetAdministrationState()
            }

            is UiState.Error -> {
                logWarn("UserManagementScreen", "createBranchState=Error: ${state.message}")
            }

            else -> {}
        }
    }

    LaunchedEffect(assignmentResult) {
        when (val state = assignmentResult) {
            is UiState.Success -> {
                logInfo("UserManagementScreen", "assignmentResult=Success; reloading users")
                viewModel.loadUsers()
                onCloseAssign()
                branchViewModel.resetAdministrationState()
            }

            is UiState.Error -> {
                logWarn("UserManagementScreen", "assignmentResult=Error: ${state.message}")
            }

            else -> {}
        }
    }

    LaunchedEffect(deleteAssignmentState) {
        when (val state = deleteAssignmentState) {
            is UiState.Success -> {
                logInfo("UserManagementScreen", "deleteAssignmentState=Success; reloading users")
                viewModel.loadUsers()
                onClearRemoveTarget()
                branchViewModel.resetAdministrationState()
            }

            is UiState.Error -> {
                logWarn("UserManagementScreen", "deleteAssignmentState=Error: ${state.message}")
            }

            else -> {}
        }
    }
}
