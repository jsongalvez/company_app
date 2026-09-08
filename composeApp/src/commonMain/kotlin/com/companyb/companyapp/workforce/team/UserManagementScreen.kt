
package com.companyb.companyapp.workforce.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.companyb.companyapp.app.navigation.NavigationContextStore
import com.companyb.companyapp.app.navigation.Route
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.identity.UserAssignmentResponse
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.workforce.AssignmentResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.workforce.branch.AssignmentRemovalTarget
import com.companyb.companyapp.workforce.branch.BranchViewModel
import com.companyb.companyapp.workforce.branch.CreateBranchDialog
import com.companyb.companyapp.workforce.branch.RemoveAssignmentDialog
import com.companyb.companyapp.workforce.team.UserSlotRow
import com.companyb.companyapp.workforce.team.UserViewModel
import com.companyb.companyapp.workforce.team.parseSlotInput
import com.companyb.companyapp.workforce.team.slotInputError

/**
 * #135 — User Management screen, locked spec #106 D2-D5.
 * #681 — reorganized into People (default) and Branches tabs without losing
 * capabilities: finding/editing a user never traverses branch setup, and
 * changing branch membership never scans all accounts.
 *
 * - People: search + Active (default)/Inactive/All + Invite user; rows carry
 *   display name, username, status text and a concise assignment summary;
 *   selection opens the grouped detail (Account, Roles, Branch assignments;
 *   Deactivate/Reactivate in the account More menu). >=1000dp list/detail
 *   side-by-side ([TeamLayoutPolicy]), below it full-width detail with Back.
 * - Branches: branch picker + selected name/type, roster in Branch Slot order,
 *   primary Assign user, secondary Create branch; slot edits and removal stay
 *   row-context actions through the existing slot policy.
 * - Tab/search/filter/selected person/branch survive detail Back, reloads and
 *   rotation (saveable); the section tab, selected person and list anchor
 *   additionally survive section switches via the section store. Filter/search
 *   empties offer Clear filters while true first use offers the invite action.
 *   A status mutation that moves its row outside the filter keeps the row
 *   pinned visible until the next interaction. Failed mutations retain their
 *   target/draft (deactivate confirms dispatch before dismissing); success
 *   retains the selection so the affected row/detail stays in view. The screen
 *   itself is route-gated on MANAGE_USERS; backend 403s surface inline and the
 *   loading gate visibly disables actions mid-flight.
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
    val states = rememberUserManagementScreenStates()
    var sectionTab by rememberSaveable(currentUserId) {
        mutableStateOf(
            teamSectionTabFromName(
                NavigationContextStore.retained(currentUserId, TEAM_CONTEXT_BRANCH, Route.UserManagement)?.tab,
            ),
        )
    }
    var statusFilter by rememberSaveable(currentUserId) {
        mutableStateOf(TeamStatusFilter.ACTIVE)
    }
    var selectedPersonId by rememberSaveable(currentUserId) {
        mutableStateOf(
            NavigationContextStore
                .retained(currentUserId, TEAM_CONTEXT_BRANCH, Route.UserManagement)
                ?.selectedId
                .takeUnless { it.isNullOrEmpty() },
        )
    }
    // Filter-excluded updated-row pin (#681): saveable so rotation keeps the
    // held row; cleared on the next search/filter/tab/select/Back interaction.
    var pinnedPersonId by rememberSaveable(currentUserId) { mutableStateOf<String?>(null) }

    UserManagementEntryEffects(
        viewModel = viewModel,
        branchViewModel = branchViewModel,
    )

    val users by viewModel.users.collectAsState()
    val heldList by viewModel.freshestUsers.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val actionErrors by viewModel.actionErrors.collectAsState()
    val assignmentResult by branchViewModel.assignmentResult.collectAsState()
    val mutationsDisabled = userManagementMutationsDisabled(viewModel, branchViewModel)
    val peopleListState =
        remember(currentUserId) {
            val anchor =
                NavigationContextStore
                    .retained(currentUserId, TEAM_CONTEXT_BRANCH, Route.UserManagement)
                    ?.scrollAnchorId
                    ?.toIntOrNull()
                    ?.coerceAtLeast(0) ?: 0
            LazyListState(firstVisibleItemIndex = anchor)
        }
    DisposableEffect(currentUserId) {
        onDispose {
            // Cleared selections persist as the empty sentinel: retain() keeps
            // the previous value on null legs, so a backed-out selection would
            // otherwise resurrect on the next section return.
            NavigationContextStore.retain(
                currentUserId,
                TEAM_CONTEXT_BRANCH,
                Route.UserManagement,
                selectedId = selectedPersonId ?: "",
                scrollAnchorId = peopleListState.firstVisibleItemIndex.toString(),
                tab = sectionTab.name,
            )
        }
    }

    UserManagementMutationEffects(
        viewModel = viewModel,
        branchViewModel = branchViewModel,
        onCloseCreateBranch = { states.onShowCreateBranchChange(false) },
        onCloseAssign = {
            states.onShowAssignDialogChange(false)
            states.onAssignmentBranchChange(null)
        },
        onClearRemoveTarget = { states.onRemoveAssignmentTargetChange(null) },
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        TeamSectionTabs(
            selected = sectionTab,
            onSelect = { next ->
                sectionTab = next
                pinnedPersonId = null
                NavigationContextStore.retain(
                    currentUserId,
                    TEAM_CONTEXT_BRANCH,
                    Route.UserManagement,
                    selectedId = selectedPersonId ?: "",
                    tab = next.name,
                )
            },
        )
        Spacer(Modifier.size(Spacing.sm))
        when (sectionTab) {
            TeamSectionTab.PEOPLE -> {
                PeopleTabContent(
                    state =
                        PeopleTabState(
                            users = users,
                            heldList = heldList,
                            searchQuery = states.searchQuery,
                            filter = statusFilter,
                            pinnedId = pinnedPersonId,
                            selectedId = selectedPersonId,
                            mutationsDisabled = mutationsDisabled,
                            actionErrors = actionErrors,
                            currentUserId = currentUserId,
                        ),
                    callbacks =
                        PeopleTabCallbacks(
                            onSearchChange = {
                                states.onSearchQueryChange(it)
                                pinnedPersonId = null
                            },
                            onFilterChange = {
                                statusFilter = it
                                pinnedPersonId = null
                            },
                            onSelect = { id ->
                                if (id != pinnedPersonId) pinnedPersonId = null
                                selectedPersonId = id
                                NavigationContextStore.retain(
                                    currentUserId,
                                    TEAM_CONTEXT_BRANCH,
                                    Route.UserManagement,
                                    selectedId = id,
                                    tab = sectionTab.name,
                                )
                            },
                            onBack = {
                                selectedPersonId = null
                                pinnedPersonId = null
                            },
                            onInvite = { states.onShowCreateUserChange(true) },
                            onRefresh = {
                                viewModel.loadUsers()
                                viewModel.loadBranches()
                            },
                            onClearFilters = {
                                states.onSearchQueryChange("")
                                statusFilter = TeamStatusFilter.ALL
                                pinnedPersonId = null
                            },
                            onRetry = viewModel::loadUsers,
                            onEditRoles = { user -> states.onRoleEditTargetChange(user) },
                            onDeactivate = { user ->
                                states.onDeactivateTargetChange(user)
                            },
                            onReactivate = { id ->
                                pinnedPersonId = id
                                viewModel.setUserStatus(id, UserStatus.ACTIVE)
                            },
                            onEditSlot = { user, assignment ->
                                states.onSlotEditTargetChange(
                                    SlotEditTarget(
                                        branchId = assignment.branchId,
                                        branchName = assignment.branchName,
                                        assignmentId = assignment.assignmentId,
                                        displayName = user.displayName,
                                        currentSlot = assignment.slot,
                                    ),
                                )
                            },
                            onRemoveAssignment = { user, assignment ->
                                branchViewModel.resetAdministrationState()
                                states.onRemoveAssignmentTargetChange(
                                    AssignmentRemovalTarget(
                                        userId = user.id,
                                        displayName = user.displayName,
                                        assignment = assignment,
                                    ),
                                )
                            },
                        ),
                    listState = peopleListState,
                )
            }

            TeamSectionTab.BRANCHES -> {
                val loadedBranches = (branches as? UiState.Success<List<BranchResponse>>)?.data.orEmpty()
                val selectedBranch = loadedBranches.firstOrNull { it.id == states.selectedBranchId }
                BranchesTab(
                    state =
                        BranchesTabState(
                            branches = branches,
                            selectedBranchId = states.selectedBranchId,
                            selectedBranch = selectedBranch,
                            slotRows =
                                if (selectedBranch == null) {
                                    emptyList()
                                } else {
                                    slotOrderForBranch(heldList.orEmpty(), selectedBranch.id)
                                },
                            actionErrors = actionErrors,
                            assignmentError =
                                branchesAssignmentError(
                                    assignmentResult,
                                    states.removeAssignmentTarget,
                                    states.showAssignUserDialog,
                                ),
                            mutationsDisabled = mutationsDisabled,
                            hasHeldUsers = heldList != null,
                        ),
                    callbacks =
                        BranchesTabCallbacks(
                            onBranchSelected = states.onSelectedBranchIdChange,
                            onRetryBranches = viewModel::loadBranches,
                            onCreateBranch = {
                                branchViewModel.resetAdministrationState()
                                states.onShowCreateBranchChange(true)
                            },
                            onAssign = {
                                val target = selectedBranch
                                if (target != null) {
                                    branchViewModel.resetAdministrationState()
                                    states.onAssignmentBranchChange(target)
                                    states.onShowAssignDialogChange(true)
                                }
                            },
                            onSwap = viewModel::swapSlots,
                            onEditSlot = states.onSlotEditTargetChange,
                        ),
                )
            }
        }
    }

    UserManagementDialogHosts(
        viewModel = viewModel,
        branchViewModel = branchViewModel,
        mutationsDisabled = mutationsDisabled,
        memberActions =
            UserManagementMemberDialogsActions(
                deactivateTarget = states.deactivateTarget,
                onDismissDeactivate = { states.onDeactivateTargetChange(null) },
                onStatusDispatched = { pinnedPersonId = it },
                slotEditTarget = states.slotEditTarget,
                onDismissSlotEdit = { states.onSlotEditTargetChange(null) },
                showCreateUserDialog = states.showCreateUserDialog,
                onCloseCreateUser = { states.onShowCreateUserChange(false) },
                roleEditTarget = states.roleEditTarget,
                onDismissRoleEdit = { states.onRoleEditTargetChange(null) },
            ),
        branchActions =
            UserManagementBranchDialogsActions(
                showCreateBranchDialog = states.showCreateBranchDialog,
                onCloseCreateBranch = { states.onShowCreateBranchChange(false) },
                showAssignUserDialog = states.showAssignUserDialog,
                assignmentBranch = states.assignmentBranch,
                onAssignDialog = states.onShowAssignDialogChange,
                onAssignmentBranchChange = states.onAssignmentBranchChange,
                removeAssignmentTarget = states.removeAssignmentTarget,
                onClearRemoveTarget = { states.onRemoveAssignmentTargetChange(null) },
            ),
    )
}

/**
 * #681 — section-store branch leg for the GLOBAL Team & branches admin.
 * The store keys per user+branch; team administration is not branch-scoped, so
 * a constant leg gives per-user tab/selection/anchor persistence without
 * implying a clocked-in branch.
 */
internal const val TEAM_CONTEXT_BRANCH = "team"

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
    actionErrors: Map<String, String>,
    memberActions: UserManagementMemberDialogsActions,
) {
    val mintState by viewModel.mintInviteResult.collectAsState()
    val rolesState by viewModel.roles.collectAsState()

    memberActions.deactivateTarget?.let { target ->
        DeactivateConfirmDialog(
            user = target,
            mutationsDisabled = mutationsDisabled,
            onDismiss = memberActions.onDismissDeactivate,
            onConfirm = {
                // #681 — dismiss only when the mutation dispatches; a guard-swallowed
                // confirm (same-frame refresh tap) retains the dialog instead of
                // losing the action silently. The pin arms the filter-excluded
                // row hold for the incoming status flip.
                if (viewModel.setUserStatus(target.id, UserStatus.INACTIVE)) {
                    memberActions.onStatusDispatched(target.id)
                    memberActions.onDismissDeactivate()
                }
            },
        )
    }

    memberActions.slotEditTarget?.let { target ->
        EditSlotDialog(
            target = target,
            options =
                SlotDialogOptions(
                    mutationsDisabled = mutationsDisabled,
                    errorMessage = actionErrors["slot:${target.branchId}:${target.assignmentId}"],
                ),
            onDismiss = memberActions.onDismissSlotEdit,
            onSave = { slot ->
                viewModel.updateSlot(target.branchId, target.assignmentId, slot) {
                    memberActions.onDismissSlotEdit()
                }
            },
        )
    }

    if (memberActions.showCreateUserDialog) {
        InviteMintDialog(
            mintState = mintState,
            onMint = viewModel::mintInvite,
            onDismiss = {
                // A mid-flight dismiss would orphan the in-flight POST (the RemittanceList
                // create-draft guard) — stay open while loading. A Success stays open too:
                // the admin needs the code until they explicitly close (closing resets).
                if (mintState !is UiState.Loading) {
                    viewModel.dismissInviteResult()
                    memberActions.onCloseCreateUser()
                }
            },
        )
    }

    memberActions.roleEditTarget?.let { target ->
        // #681 — the dialog retains its draft on failure (the slot-edit precedent)
        // and renders the failure inline: dismiss runs only on 2xx via
        // afterSuccess, so the error must surface here, not just in the detail
        // behind the modal.
        RoleEditDialog(
            user = target,
            rolesState = rolesState,
            mutationsDisabled = mutationsDisabled,
            actions =
                RoleEditActions(
                    onRetryRoles = viewModel::loadRoles,
                    onDismiss = memberActions.onDismissRoleEdit,
                    onSave = { selected ->
                        viewModel.replaceRoles(target.id, selected) {
                            memberActions.onDismissRoleEdit()
                        }
                    },
                ),
            errorMessage = actionErrors["roles:${target.id}"],
        )
    }
}

/**
 * #479 — the branch-dialog flow states as one carrier (data classes are LPL-free).
 */
data class BranchDialogsStates(
    val createBranchState: UiState<BranchResponse>,
    val assignmentResult: UiState<AssignmentResponse>,
    val deleteAssignmentState: UiState<Unit>,
)

/**
 * Branch-admin dialogs of the User Management screen (companion: [UserManagementMemberDialogs]).
 * Same #462 hoist — create/assign/remove overlays, order-independent. `assignmentDialogBranch`
 * derives inside so the Screen call site stays lean.
 */
@Composable
internal fun UserManagementBranchDialogs(
    viewModel: UserViewModel,
    branchViewModel: BranchViewModel,
    mutationsDisabled: Boolean,
    states: BranchDialogsStates,
    branchActions: UserManagementBranchDialogsActions,
) {
    val createBranchState = states.createBranchState
    val assignmentResult = states.assignmentResult
    val deleteAssignmentState = states.deleteAssignmentState
    if (branchActions.showCreateBranchDialog) {
        val loadedBranches =
            (viewModel.branches.collectAsState().value as? UiState.Success<List<BranchResponse>>)?.data.orEmpty()
        CreateBranchDialog(
            state = createBranchState,
            existingBranches = loadedBranches,
            mutationsDisabled = mutationsDisabled,
            onCreate = branchViewModel::createBranch,
            onDismiss = {
                branchViewModel.resetAdministrationState()
                branchActions.onCloseCreateBranch()
            },
        )
    }

    UserManagementAssignDialog(
        viewModel = viewModel,
        branchViewModel = branchViewModel,
        mutationsDisabled = mutationsDisabled,
        assignmentResult = assignmentResult,
        branchActions = branchActions,
    )

    branchActions.removeAssignmentTarget?.let { target ->
        RemoveAssignmentDialog(
            target = target,
            state = deleteAssignmentState,
            mutationsDisabled = mutationsDisabled,
            onRemove = { branchViewModel.deleteAssignment(target.assignment.branchId, target.assignment.assignmentId) },
            onDismiss = {
                branchViewModel.resetAdministrationState()
                branchActions.onClearRemoveTarget()
            },
        )
    }
}

/**
 * #462 LPL burn — the two slot-order callbacks as one object (expect + 3 actuals + mobile
 * helper each drop 6 params to 5; data classes are LPL-free, the BranchSelect precedent).
 */
data class SlotOrderCallbacks(
    val onSwap: (assignmentIdA: String, assignmentIdB: String) -> Unit,
    val onEditSlot: (row: UserSlotRow) -> Unit,
)

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
@Suppress("UnusedParameter") // #135 android tap-to-edit — onSwap kept for UserSlotOrderList symmetry; #598 keep
fun MobileUserSlotOrderList(
    branchName: String,
    rows: List<UserSlotRow>,
    mutationsDisabled: Boolean,
    callbacks: SlotOrderCallbacks,
    errors: List<String>,
) {
    UserSlotOrderCard(branchName = branchName, isEmpty = rows.isEmpty(), errors = errors) {
        rows.forEach { row ->
            val tappable = !row.isDeactivated && !mutationsDisabled
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = tappable) { callbacks.onEditSlot(row) }
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
    callbacks: SlotOrderCallbacks,
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
            branches = branches,
            selectedBranchId = selectedBranchId,
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
    branches: UiState<List<BranchResponse>>,
    selectedBranchId: String?,
    expanded: Boolean,
    disabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val isError = branches is UiState.Error
    val isLoading = branches is UiState.Loading
    val isIdle = branches is UiState.Idle
    val selectedLabel =
        (branches as? UiState.Success<List<BranchResponse>>)
            ?.data
            ?.firstOrNull { it.id == selectedBranchId }
            ?.let { "${it.name} (${it.branchType})" }
            ?: "Select a branch"
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

internal val BranchResponseSaver =
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

internal val AssignmentRemovalTargetSaver =
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
