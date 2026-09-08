package com.companyb.companyapp.workforce.team

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.identity.UserSummaryResponse
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.workforce.branch.AssignmentRemovalTarget
import com.companyb.companyapp.workforce.branch.BranchViewModel
import com.companyb.companyapp.workforce.team.UserViewModel

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
    val actionErrors by viewModel.actionErrors.collectAsState()
    val createBranchState by branchViewModel.createBranchState.collectAsState()
    val assignmentResult by branchViewModel.assignmentResult.collectAsState()
    val deleteAssignmentState by branchViewModel.deleteAssignmentState.collectAsState()

    UserManagementMemberDialogs(
        viewModel = viewModel,
        mutationsDisabled = mutationsDisabled,
        actionErrors = actionErrors,
        memberActions = memberActions,
    )

    UserManagementBranchDialogs(
        viewModel = viewModel,
        branchViewModel = branchViewModel,
        mutationsDisabled = mutationsDisabled,
        states = BranchDialogsStates(createBranchState, assignmentResult, deleteAssignmentState),
        branchActions = branchActions,
    )
}

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
 * Dialog + UI states hoisted out of [UserManagementScreen] for the #462 LongMethod
 * burn-down. Lives here (not Header.kt) because Header.kt sits at the detekt
 * file-function wall (10/11) — Overlays.kt has fresh budget. Owns the dialog + UI
 * remember/rememberSaveable states verbatim (incl. custom Savers) so the Screen keeps
 * one slim call; values + single-line setters ride [UserManagementScreenStates]
 * (data class so LongParameterList/TooManyFunctions-free). Unconditional call
 * preserves saveable lifetimes.
 */
@Composable
internal fun rememberUserManagementScreenStates(): UserManagementScreenStates {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    var deactivateTarget by remember { mutableStateOf<UserSummaryResponse?>(null) }
    var slotEditTarget by
        rememberSaveable(stateSaver = SlotEditTargetSaver) {
            mutableStateOf<SlotEditTarget?>(null)
        }
    var showCreateUserDialog by rememberSaveable { mutableStateOf(false) }
    var roleEditTarget by remember { mutableStateOf<UserSummaryResponse?>(null) }
    var showCreateBranchDialog by rememberSaveable { mutableStateOf(false) }
    var showAssignUserDialog by rememberSaveable { mutableStateOf(false) }
    var assignmentBranch by rememberSaveable(stateSaver = BranchResponseSaver) {
        mutableStateOf<BranchResponse?>(null)
    }
    var removeAssignmentTarget by
        rememberSaveable(stateSaver = AssignmentRemovalTargetSaver) {
            mutableStateOf<AssignmentRemovalTarget?>(null)
        }
    return UserManagementScreenStates(
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        selectedBranchId = selectedBranchId,
        onSelectedBranchIdChange = { selectedBranchId = it },
        deactivateTarget = deactivateTarget,
        onDeactivateTargetChange = { deactivateTarget = it },
        slotEditTarget = slotEditTarget,
        onSlotEditTargetChange = { slotEditTarget = it },
        showCreateUserDialog = showCreateUserDialog,
        onShowCreateUserChange = { showCreateUserDialog = it },
        roleEditTarget = roleEditTarget,
        onRoleEditTargetChange = { roleEditTarget = it },
        showCreateBranchDialog = showCreateBranchDialog,
        onShowCreateBranchChange = { showCreateBranchDialog = it },
        showAssignUserDialog = showAssignUserDialog,
        onShowAssignDialogChange = { showAssignUserDialog = it },
        assignmentBranch = assignmentBranch,
        onAssignmentBranchChange = { assignmentBranch = it },
        removeAssignmentTarget = removeAssignmentTarget,
        onRemoveAssignmentTargetChange = { removeAssignmentTarget = it },
    )
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
