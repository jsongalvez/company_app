@file:Suppress("DEPRECATION") // #563 workforce team owner, grandfather retarget

package com.companyb.companyapp.workforce.team

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.UserAssignmentResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
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
 *   the #94-grad capability wiring populates AppSessionState.snapshot — documented state, not
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
    val states = rememberUserManagementScreenStates()

    UserManagementEntryEffects(
        viewModel = viewModel,
        branchViewModel = branchViewModel,
    )

    val derived = rememberUserManagementDerived(viewModel, states.searchQuery, states.selectedBranchId)
    val mutationsDisabled = userManagementMutationsDisabled(viewModel, branchViewModel)

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
        UserManagementTopSectionsHost(viewModel, branchViewModel, states, derived, mutationsDisabled)

        UserManagementUserListHost(viewModel, branchViewModel, currentUserId, states, derived)
    }

    UserManagementDialogHosts(
        viewModel = viewModel,
        branchViewModel = branchViewModel,
        mutationsDisabled = mutationsDisabled,
        memberActions =
            UserManagementMemberDialogsActions(
                deactivateTarget = states.deactivateTarget,
                onDismissDeactivate = { states.onDeactivateTargetChange(null) },
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
                memberActions.onDismissDeactivate()
                viewModel.setUserStatus(target.id, UserStatus.INACTIVE)
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
        // Save closes the dialog immediately (the slot-edit precedent); a failed PUT surfaces
        // as the "roles:$userId" inline error in the still-expanded row below the action row.
        RoleEditDialog(
            user = target,
            rolesState = rolesState,
            mutationsDisabled = mutationsDisabled,
            actions =
                RoleEditActions(
                    onRetryRoles = viewModel::loadRoles,
                    onDismiss = memberActions.onDismissRoleEdit,
                    onSave = { selected ->
                        memberActions.onDismissRoleEdit()
                        viewModel.replaceRoles(target.id, selected)
                    },
                ),
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
@Suppress("UnusedParameter") // #135 android tap-to-edit — onSwap kept for UserSlotOrderList symmetry
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

/**
 * #479 — row-level mutations as one carrier (data classes are LPL-free): flags + the
 * deactivate/reactivate/roles/slot/remove callbacks shared by [UserRow] and
 * [UserRowExpandedBody].
 */
data class UserRowActions(
    val currentUserId: String?,
    val mutationsDisabled: Boolean,
    val onDeactivate: () -> Unit,
    val onReactivate: () -> Unit,
    val onEditRoles: () -> Unit,
    val onEditSlot: (UserAssignmentResponse) -> Unit,
    val onRemoveAssignment: (UserAssignmentResponse) -> Unit,
)

@Composable
internal fun UserRow(
    user: UserSummaryResponse,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    rowActions: UserRowActions,
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
                    canDeactivate = !isDeactivated && user.id != rowActions.currentUserId,
                    rowActions = rowActions,
                )

                if (user.id == rowActions.currentUserId) {
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
    rowActions: UserRowActions,
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
                    onClick = { rowActions.onEditSlot(assignment) },
                    enabled = !isDeactivated && !rowActions.mutationsDisabled,
                ) {
                    Text("Edit slot")
                }
                TextButton(
                    onClick = { rowActions.onRemoveAssignment(assignment) },
                    enabled = !rowActions.mutationsDisabled,
                ) {
                    Text("Remove")
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = rowActions.onEditRoles, enabled = !rowActions.mutationsDisabled) {
            Text("Edit roles")
        }
        if (canDeactivate) {
            TextButton(onClick = rowActions.onDeactivate, enabled = !rowActions.mutationsDisabled) {
                Text(
                    text = "Deactivate",
                    // Dimmed via M3's disabledContentColor when gated mid-load —
                    // the explicit error color would keep it vivid red (pass-4 SOFT,
                    // the dialog conditional's principle).
                    color =
                        if (rowActions.mutationsDisabled) {
                            Color.Unspecified
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }
        }
        if (isDeactivated) {
            TextButton(onClick = rowActions.onReactivate, enabled = !rowActions.mutationsDisabled) {
                Text("Reactivate")
            }
        }
    }
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
