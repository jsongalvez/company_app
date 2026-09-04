@file:Suppress("DEPRECATION")

package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.UserAssignmentResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.BranchViewModel
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.UserSlotRow
import com.companyb.companyapp.viewmodel.UserViewModel
import com.companyb.companyapp.viewmodel.filterUsers
import com.companyb.companyapp.viewmodel.parseSlotInput
import com.companyb.companyapp.viewmodel.slotInputError
import com.companyb.companyapp.viewmodel.slotOrderForBranch

/**
 * #135 — User Management screen, locked spec #106 D2-D5.
 *
 * - D2 — flat user list (displayName/username/status badge/"deactivated X ago"/assigned branches
 *   with slots), client-side search, expandable rows (per-assignment slot edit + deactivate/
 *   reactivate toggle), deactivated rows dimmed with slot controls disabled.
 * - D3 — deactivate = confirmation dialog (login blocked immediately, capabilities gone, records
 *   + assignments kept) → existing PATCH; reactivate = direct row action → symmetric PATCH; the
 *   deactivate action is hidden on the own row; 2xx updates the row in place (pessimistic,
 *   ADR-0022).
 * - D4 — branch dropdown (branchType shown, `GET /api/branches` — this screen's holder
 *   legitimately owns GLOBAL MANAGE_USERS) + slot-order list for the selected branch: desktop =
 *   up/down arrows → pairwise `POST /slots/swap`; mobile = tap-to-edit slot number → PATCH slot;
 *   manual number input available on both as fallback (#95 — no desktop-only behavior leaks to
 *   mobile). Duplicates tolerated (BR:67) — no renumber cascade.
 * - D5 — single route pushed on both platforms (wired in both NavHosts, code-only MANAGE_USERS
 *   route gate per #99 D7; backend 403 stays authoritative). The drawer item stays hidden until
 *   the #94-grad capability wiring populates SessionState.capabilities — documented state, not
 *   hacked around (ticket note).
 *
 * Mutations never optimistically mutate: a 2xx applies the in-place list update, any failure
 * keeps the row and surfaces an inline per-action error keyed by the same key as the in-flight
 * guard (`inFlight`/`actionErrors`).
 */
@Composable
fun UserManagementScreen(
    viewModel: UserViewModel,
    branchViewModel: BranchViewModel,
    currentUserId: String?,
) {
    val users by viewModel.users.collectAsState()
    // Keep-last render source (#162 — the KeepLast freshest flow, the #143 VM-held-list shape):
    // Success data, or the VM-held mirror during Loading/Error so a reload never flashes the
    // spinner over held rows and a failed reload never replaces the list with an ErrorCard.
    // null only when nothing has ever loaded (first composition) — spinner/ErrorCard then.
    val heldList by viewModel.freshestUsers.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val inFlight by viewModel.inFlight.collectAsState()
    val actionErrors by viewModel.actionErrors.collectAsState()
    val createBranchState by branchViewModel.createBranchState.collectAsState()
    val assignmentResult by branchViewModel.assignmentResult.collectAsState()
    val deleteAssignmentState by branchViewModel.deleteAssignmentState.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
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

    UserManagementEntryEffects(
        viewModel = viewModel,
        assignmentResult = assignmentResult,
        deleteAssignmentState = deleteAssignmentState,
        createBranchState = createBranchState,
    )

    val loadedUsers = heldList.orEmpty()
    val filteredUsers = remember(loadedUsers, searchQuery) { filterUsers(loadedUsers, searchQuery) }
    val loadedBranches = (branches as? UiState.Success<List<BranchResponse>>)?.data.orEmpty()
    val selectedBranch = loadedBranches.firstOrNull { it.id == selectedBranchId }
    val slotRows =
        remember(loadedUsers, selectedBranchId, selectedBranch) {
            if (selectedBranch == null) emptyList() else slotOrderForBranch(loadedUsers, selectedBranch.id)
        }
    val selectedBranchName = selectedBranch?.name
    // Mutations disabled while one is in flight (ADR-0022) OR while a reload is in flight: the
    // keep-last gate renders live rows during Loading, and a mutation landing mid-load would be
    // clobbered by the load's pre-mutation snapshot (pass-1 HARD — the VM guard covers the
    // same-frame tap; this gate is the visible affordance).
    val mutationsDisabled =
        inFlight.isNotEmpty() ||
            users is UiState.Loading ||
            branches is UiState.Loading ||
            createBranchState is UiState.Loading ||
            assignmentResult is UiState.Loading ||
            deleteAssignmentState is UiState.Loading

    UserMutationEffects(
        createBranchState = createBranchState,
        assignmentResult = assignmentResult,
        deleteAssignmentState = deleteAssignmentState,
        viewModel = viewModel,
        branchViewModel = branchViewModel,
        onCloseCreateBranchDialog = { showCreateBranchDialog = false },
        onCloseAssignDialog = {
            showAssignUserDialog = false
            assignmentBranch = null
        },
        onClearRemoveTarget = { removeAssignmentTarget = null },
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        UserManagementTopSections(
            searchQuery = searchQuery,
            branches = branches,
            selectedBranchId = selectedBranchId,
            selectedBranch = selectedBranch,
            actions =
                userManagementTopSectionsActions(
                    users = users,
                    heldNonNull = heldList != null,
                    mutationsDisabled = mutationsDisabled,
                    callbacks =
                        UserManagementTopSectionsCallbacks(
                            onSearchChange = { searchQuery = it },
                            onShowCreateUser = { showCreateUserDialog = it },
                            onLoadUsers = viewModel::loadUsers,
                            onLoadBranches = viewModel::loadBranches,
                            onBranchSelected = { selectedBranchId = it },
                            onResetAdministration = branchViewModel::resetAdministrationState,
                            onShowCreateBranch = { showCreateBranchDialog = it },
                            onAssignmentBranchChange = { assignmentBranch = it },
                            onShowAssignDialog = { showAssignUserDialog = it },
                            selectedBranch = selectedBranch,
                            assignmentResult = assignmentResult,
                            removeAssignmentTarget = removeAssignmentTarget,
                            showAssignDialog = showAssignUserDialog,
                        ),
                ),
        )

        val userRowActions =
            userManagementUserRowActions(
                currentUserId = currentUserId,
                mutationsDisabled = mutationsDisabled,
                selectedBranchId = selectedBranchId,
                actionErrors = actionErrors,
                callbacks =
                    UserManagementUserRowCallbacks(
                        expandedIds = expandedIds,
                        onExpandedIdsChange = { expandedIds = it },
                        onDeactivateTarget = { deactivateTarget = it },
                        onRoleEditTarget = { roleEditTarget = it },
                        onSlotEditTarget = { slotEditTarget = it },
                        onRemoveTarget = { removeAssignmentTarget = it },
                        onReactivate = { id -> viewModel.setUserStatus(id, UserStatus.ACTIVE) },
                        onResetAdministration = branchViewModel::resetAdministrationState,
                    ),
            )

        UserManagementUserList(
            users = users,
            heldNonNull = heldList != null,
            filteredUsers = filteredUsers,
            selectedBranch = selectedBranch,
            actions =
                UserManagementUserListActions(
                    selectedBranchName = selectedBranchName ?: "",
                    slotRows = slotRows,
                    mutationsDisabled = mutationsDisabled,
                    searchQuery = searchQuery,
                    expandedIds = expandedIds,
                    userRowActions = userRowActions,
                    actionErrors = actionErrors,
                    onSwapSlots = viewModel::swapSlots,
                    onEditSlotTarget = { slotEditTarget = it },
                    onRetry = viewModel::loadUsers,
                ),
        )
    }

    UserManagementDialogHosts(
        viewModel = viewModel,
        branchViewModel = branchViewModel,
        mutationsDisabled = mutationsDisabled,
        memberActions =
            UserManagementMemberDialogsActions(
                deactivateTarget = deactivateTarget,
                onDismissDeactivate = { deactivateTarget = null },
                slotEditTarget = slotEditTarget,
                onDismissSlotEdit = { slotEditTarget = null },
                showCreateUserDialog = showCreateUserDialog,
                onCloseCreateUser = { showCreateUserDialog = false },
                roleEditTarget = roleEditTarget,
                onDismissRoleEdit = { roleEditTarget = null },
            ),
        branchActions =
            UserManagementBranchDialogsActions(
                showCreateBranchDialog = showCreateBranchDialog,
                onCloseCreateBranch = { showCreateBranchDialog = false },
                showAssignUserDialog = showAssignUserDialog,
                assignmentBranch = assignmentBranch,
                onAssignDialog = { showAssignUserDialog = it },
                onAssignmentBranchChange = { assignmentBranch = it },
                removeAssignmentTarget = removeAssignmentTarget,
                onClearRemoveTarget = { removeAssignmentTarget = null },
            ),
    )
}

/**
 * Member-lifecycle dialogs of the User Management screen, hoisted out of [UserManagementScreen]
 * for the #462 LongMethod burn-down (companion: [UserManagementBranchDialogs] for branch-admin
 * dialogs). Overlays only — order-independent, no list/focus state. `mintState`/`rolesState`
 * are collected here (no body surface reads them); dismiss setters stay at the Screen call
 * site so it stays lean.
 */
@Composable
internal fun UserManagementMemberDialogs(
    viewModel: UserViewModel,
    mutationsDisabled: Boolean,
    deactivateTarget: UserSummaryResponse?,
    onDismissDeactivate: () -> Unit,
    slotEditTarget: SlotEditTarget?,
    actionErrors: Map<String, String>,
    onDismissSlotEdit: () -> Unit,
    showCreateUserDialog: Boolean,
    onCloseCreateUser: () -> Unit,
    roleEditTarget: UserSummaryResponse?,
    onDismissRoleEdit: () -> Unit,
) {
    val mintState by viewModel.mintInviteResult.collectAsState()
    val rolesState by viewModel.roles.collectAsState()

    deactivateTarget?.let { target ->
        DeactivateConfirmDialog(
            user = target,
            mutationsDisabled = mutationsDisabled,
            onDismiss = onDismissDeactivate,
            onConfirm = {
                onDismissDeactivate()
                viewModel.setUserStatus(target.id, UserStatus.INACTIVE)
            },
        )
    }

    slotEditTarget?.let { target ->
        EditSlotDialog(
            target = target,
            options =
                SlotDialogOptions(
                    mutationsDisabled = mutationsDisabled,
                    errorMessage = actionErrors["slot:${target.branchId}:${target.assignmentId}"],
                ),
            onDismiss = onDismissSlotEdit,
            onSave = { slot ->
                viewModel.updateSlot(target.branchId, target.assignmentId, slot) {
                    onDismissSlotEdit()
                }
            },
        )
    }

    if (showCreateUserDialog) {
        InviteMintDialog(
            mintState = mintState,
            onMint = viewModel::mintInvite,
            onDismiss = {
                // A mid-flight dismiss would orphan the in-flight POST (the RemittanceList
                // create-draft guard) — stay open while loading. A Success stays open too:
                // the admin needs the code until they explicitly close (closing resets).
                if (mintState !is UiState.Loading) {
                    viewModel.dismissInviteResult()
                    onCloseCreateUser()
                }
            },
        )
    }

    roleEditTarget?.let { target ->
        // Save closes the dialog immediately (the slot-edit precedent); a failed PUT surfaces
        // as the "roles:$userId" inline error in the still-expanded row below the action row.
        RoleEditDialog(
            user = target,
            rolesState = rolesState,
            mutationsDisabled = mutationsDisabled,
            actions =
                RoleEditActions(
                    onRetryRoles = viewModel::loadRoles,
                    onDismiss = onDismissRoleEdit,
                    onSave = { selected ->
                        onDismissRoleEdit()
                        viewModel.replaceRoles(target.id, selected)
                    },
                ),
        )
    }
}

/**
 * Branch-admin dialogs of the User Management screen (companion: [UserManagementMemberDialogs]).
 * Same #462 hoist — create/assign/remove overlays, order-independent. `assignmentDialogBranch`
 * derives inside so the Screen call site stays lean.
 */
@Composable
internal fun UserManagementBranchDialogs(
    branchViewModel: BranchViewModel,
    mutationsDisabled: Boolean,
    showCreateBranchDialog: Boolean,
    createBranchState: UiState<BranchResponse>,
    loadedBranches: List<BranchResponse>,
    onCloseCreateBranch: () -> Unit,
    showAssignUserDialog: Boolean,
    assignmentBranch: BranchResponse?,
    loadedUsers: List<UserSummaryResponse>,
    assignmentResult: UiState<AssignmentResponse>,
    onCloseAssign: () -> Unit,
    removeAssignmentTarget: AssignmentRemovalTarget?,
    deleteAssignmentState: UiState<Unit>,
    onClearRemoveTarget: () -> Unit,
) {
    if (showCreateBranchDialog) {
        CreateBranchDialog(
            state = createBranchState,
            existingBranches = loadedBranches,
            mutationsDisabled = mutationsDisabled,
            onCreate = branchViewModel::createBranch,
            onDismiss = {
                branchViewModel.resetAdministrationState()
                onCloseCreateBranch()
            },
        )
    }

    val assignmentDialogBranch = assignmentBranch
    if (showAssignUserDialog && assignmentDialogBranch != null) {
        AssignUserDialog(
            branch = assignmentDialogBranch,
            users = loadedUsers,
            state = assignmentResult,
            mutationsDisabled = mutationsDisabled,
            actions =
                AssignmentDialogActions(
                    onAssign = { request -> branchViewModel.createAssignment(assignmentDialogBranch.id, request) },
                    onDismiss = {
                        branchViewModel.resetAdministrationState()
                        onCloseAssign()
                    },
                ),
        )
    }

    removeAssignmentTarget?.let { target ->
        RemoveAssignmentDialog(
            target = target,
            state = deleteAssignmentState,
            mutationsDisabled = mutationsDisabled,
            onRemove = { branchViewModel.deleteAssignment(target.assignment.branchId, target.assignment.assignmentId) },
            onDismiss = {
                branchViewModel.resetAdministrationState()
                onClearRemoveTarget()
            },
        )
    }
}

/**
 * #135 D4 — platform-split slot-order list: desktop = up/down arrows (pairwise swap with the
 * neighbor row) + an edit button (manual number fallback); android = tap-to-edit (PATCH slot).
 * Rows arrive slot ASC (display name tiebreak); deactivated rows are dimmed with disabled
 * controls (D2). `errors` renders the branch's inline swap + slot-edit errors (ADR-0022). The
 * shared card chrome (Surface/header/empty-state/errors) lives in [UserSlotOrderCard]; each
 * platform actual supplies only the row content. `onEditSlot` receives the tapped row (the
 * screen builds the edit target straight from it — no id lookup, no silent no-op).
 */
@Composable
fun MobileUserSlotOrderList(
    branchName: String,
    rows: List<UserSlotRow>,
    mutationsDisabled: Boolean,
    onSwap: (assignmentIdA: String, assignmentIdB: String) -> Unit,
    onEditSlot: (row: UserSlotRow) -> Unit,
    errors: List<String>,
) {
    UserSlotOrderCard(branchName = branchName, isEmpty = rows.isEmpty(), errors = errors) {
        rows.forEach { row ->
            val tappable = !row.isDeactivated && !mutationsDisabled
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = tappable) { onEditSlot(row) }
                        .rowHover(enabled = tappable)
                        .alpha(if (row.isDeactivated) DEACTIVATED_ROW_ALPHA else 1f)
                        .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    "#${row.slot}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(row.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (tappable) {
                    Text(
                        "Edit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
expect fun UserSlotOrderList(
    branchName: String,
    rows: List<UserSlotRow>,
    mutationsDisabled: Boolean,
    onSwap: (assignmentIdA: String, assignmentIdB: String) -> Unit,
    onEditSlot: (row: UserSlotRow) -> Unit,
    errors: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BranchPicker(
    branches: UiState<List<BranchResponse>>,
    selectedBranchId: String?,
    onBranchSelected: (String?) -> Unit,
    onRetryBranches: () -> Unit,
    disabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val isError = branches is UiState.Error
    val isIdle = branches is UiState.Idle
    val isLoading = branches is UiState.Loading
    val options = (branches as? UiState.Success<List<BranchResponse>>)?.data.orEmpty()
    val selectedLabel =
        options.firstOrNull { it.id == selectedBranchId }?.let { "${it.name} (${it.branchType})" }
            ?: "Select a branch"

    LaunchedEffect(branches) {
        if (branches is UiState.Error) {
            logWarn("UserManagementScreen", "branchesState=Error: ${branches.message}")
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (!disabled) {
                if (isError || isIdle) {
                    onRetryBranches()
                } else if (!isLoading) {
                    expanded = !expanded
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        BranchPickerField(
            isError = isError,
            isLoading = isLoading,
            isIdle = isIdle,
            selectedLabel = selectedLabel,
            expanded = expanded,
            disabled = disabled,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("No branch") },
                onClick = {
                    onBranchSelected(null)
                    expanded = false
                },
            )
            options.forEach { branch ->
                DropdownMenuItem(
                    text = { Text("${branch.name} (${branch.branchType})") },
                    onClick = {
                        onBranchSelected(branch.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchPickerField(
    isError: Boolean,
    isLoading: Boolean,
    isIdle: Boolean,
    selectedLabel: String,
    expanded: Boolean,
    disabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value =
            when {
                isError -> "Branches unavailable — tap to retry"
                isLoading || isIdle -> "Loading branches…"
                else -> selectedLabel
            },
        onValueChange = {},
        readOnly = true,
        label = { Text("Slot order — branch") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier = modifier,
        enabled = !disabled && !isLoading && !isIdle,
    )
}

@Composable
private fun UserMutationEffects(
    createBranchState: UiState<BranchResponse>,
    assignmentResult: UiState<AssignmentResponse>,
    deleteAssignmentState: UiState<Unit>,
    viewModel: UserViewModel,
    branchViewModel: BranchViewModel,
    onCloseCreateBranchDialog: () -> Unit,
    onCloseAssignDialog: () -> Unit,
    onClearRemoveTarget: () -> Unit,
) {
    LaunchedEffect(createBranchState) {
        when (val state = createBranchState) {
            is UiState.Success -> {
                logInfo("UserManagementScreen", "createBranchState=Success; reloading branches")
                viewModel.loadBranches()
                onCloseCreateBranchDialog()
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
                onCloseAssignDialog()
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

@Composable
internal fun UserRow(
    user: UserSummaryResponse,
    currentUserId: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    mutationsDisabled: Boolean,
    onDeactivate: () -> Unit,
    onReactivate: () -> Unit,
    onEditRoles: () -> Unit,
    onEditSlot: (UserAssignmentResponse) -> Unit,
    onRemoveAssignment: (UserAssignmentResponse) -> Unit,
    errors: List<String>,
) {
    val isDeactivated = user.status == UserStatus.INACTIVE
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(if (isDeactivated) DEACTIVATED_ROW_ALPHA else 1f)
                    .padding(Spacing.md),
        ) {
            UserRowHeader(
                user = user,
                onToggleExpanded = onToggleExpanded,
            )

            if (expanded) {
                UserRowExpandedBody(
                    user = user,
                    isDeactivated = isDeactivated,
                    canDeactivate = !isDeactivated && user.id != currentUserId,
                    mutationsDisabled = mutationsDisabled,
                    onEditSlot = onEditSlot,
                    onRemoveAssignment = onRemoveAssignment,
                    onEditRoles = onEditRoles,
                    onDeactivate = onDeactivate,
                    onReactivate = onReactivate,
                )

                if (user.id == currentUserId) {
                    Text(
                        text = "You can't deactivate your own account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                errors.forEach { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserRowExpandedBody(
    user: UserSummaryResponse,
    isDeactivated: Boolean,
    canDeactivate: Boolean,
    mutationsDisabled: Boolean,
    onEditSlot: (UserAssignmentResponse) -> Unit,
    onRemoveAssignment: (UserAssignmentResponse) -> Unit,
    onEditRoles: () -> Unit,
    onDeactivate: () -> Unit,
    onReactivate: () -> Unit,
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))

    if (user.assignments.isEmpty()) {
        Text(
            text = "No branch assignments",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        user.assignments.forEach { assignment ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${assignment.branchName} — slot ${assignment.slot}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onEditSlot(assignment) },
                    enabled = !isDeactivated && !mutationsDisabled,
                ) {
                    Text("Edit slot")
                }
                TextButton(
                    onClick = { onRemoveAssignment(assignment) },
                    enabled = !mutationsDisabled,
                ) {
                    Text("Remove")
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onEditRoles, enabled = !mutationsDisabled) {
            Text("Edit roles")
        }
        if (canDeactivate) {
            TextButton(onClick = onDeactivate, enabled = !mutationsDisabled) {
                Text(
                    text = "Deactivate",
                    // Dimmed via M3's disabledContentColor when gated mid-load —
                    // the explicit error color would keep it vivid red (pass-4 SOFT,
                    // the dialog conditional's principle).
                    color =
                        if (mutationsDisabled) {
                            Color.Unspecified
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }
        }
        if (isDeactivated) {
            TextButton(onClick = onReactivate, enabled = !mutationsDisabled) {
                Text("Reactivate")
            }
        }
    }
}

private val BranchResponseSaver =
    Saver<BranchResponse?, List<String>>(
        save = { branch ->
            branch?.let { listOf(it.id, it.name, it.branchType.name) } ?: emptyList()
        },
        restore = { values ->
            if (values.size != 3) {
                null
            } else {
                BranchType.entries
                    .firstOrNull { it.name == values[2] }
                    ?.let { BranchResponse(id = values[0], name = values[1], branchType = it) }
            }
        },
    )

private val AssignmentRemovalTargetSaver =
    Saver<AssignmentRemovalTarget?, List<String>>(
        save = { target ->
            target?.let {
                listOf(
                    it.userId,
                    it.displayName,
                    it.assignment.branchId,
                    it.assignment.branchName,
                    it.assignment.slot.toString(),
                    it.assignment.assignmentId,
                )
            } ?: emptyList()
        },
        restore = { values ->
            if (values.size != ASSIGNMENT_REMOVAL_TARGET_VALUE_COUNT) {
                null
            } else {
                values[ASSIGNMENT_REMOVAL_SLOT_INDEX].toShortOrNull()?.let { slot ->
                    AssignmentRemovalTarget(
                        userId = values[ASSIGNMENT_REMOVAL_USER_ID_INDEX],
                        displayName = values[ASSIGNMENT_REMOVAL_DISPLAY_NAME_INDEX],
                        assignment =
                            UserAssignmentResponse(
                                assignmentId = values[ASSIGNMENT_REMOVAL_ASSIGNMENT_ID_INDEX],
                                branchId = values[ASSIGNMENT_REMOVAL_BRANCH_ID_INDEX],
                                branchName = values[ASSIGNMENT_REMOVAL_BRANCH_NAME_INDEX],
                                slot = slot,
                            ),
                    )
                }
            }
        },
    )

private const val ASSIGNMENT_REMOVAL_TARGET_VALUE_COUNT = 6
private const val ASSIGNMENT_REMOVAL_USER_ID_INDEX = 0
private const val ASSIGNMENT_REMOVAL_DISPLAY_NAME_INDEX = 1
private const val ASSIGNMENT_REMOVAL_BRANCH_ID_INDEX = 2
private const val ASSIGNMENT_REMOVAL_BRANCH_NAME_INDEX = 3
private const val ASSIGNMENT_REMOVAL_SLOT_INDEX = 4
private const val ASSIGNMENT_REMOVAL_ASSIGNMENT_ID_INDEX = 5

// Shared across common + both platform actuals (#135 D2 dimmed-rows treatment).
internal const val DEACTIVATED_ROW_ALPHA = 0.55f
