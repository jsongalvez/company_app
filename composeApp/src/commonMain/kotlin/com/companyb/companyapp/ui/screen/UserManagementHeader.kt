package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.UserSlotRow

/**
 * Title + Invite/Refresh actions + client-side search field of the User Management screen,
 * hoisted out of [UserManagementScreen] for the #462 LongMethod burn-down. Lives in its own
 * file because both UserManagementScreen.kt and UserManagementDialogs.kt sit at the detekt
 * file-function wall (10/11) — a same-file helper trips TooManyFunctions. Gates arrive as
 * booleans so the Screen keeps the derivations and this host stays a pure overlay. The
 * callbacks + gates ride a [UserManagementHeaderActions] (RoleEditActions precedent) so the
 * helper stays LongParameterList-clean outside the LPL-excluded Screen file.
 */
@Composable
internal fun UserManagementHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    searchEnabled: Boolean,
    actions: UserManagementHeaderActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "User Management",
            style = MaterialTheme.typography.titleLarge,
        )
        Row {
            TextButton(
                onClick = actions.onInvite,
                enabled = actions.inviteEnabled,
            ) {
                Text("Invite user")
            }
            TextButton(
                onClick = actions.onRefresh,
                // A refresh landing mid-mutation lets the mutation's in-place transform re-apply
                // to the fresh list (swap would double-apply — pass-1 P2/P4 HARD). Belt: the
                // button gate here; suspenders: UserViewModel.loadUsers also skips while any
                // mutation is in flight (covers non-click triggers like LaunchedEffect refires).
                enabled = actions.refreshEnabled,
            ) {
                Text("Refresh")
            }
        }
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        label = { Text("Search users") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = searchEnabled,
    )
}

/**
 * Branch picker + branch-administration row hoisted out of [UserManagementScreen] for the #462
 * LongMethod burn-down. Lives here (not same-file) because UserManagementScreen.kt sits at the
 * detekt file-function wall — a same-file helper trips TooManyFunctions. Gates arrive as
 * booleans + a derived error string so the Screen keeps the derivations and this host stays a
 * pure overlay; callbacks ride [UserManagementBranchAdminActions] so the signature stays
 * LongParameterList-clean.
 */
@Composable
internal fun UserManagementBranchAdmin(
    branches: UiState<List<BranchResponse>>,
    selectedBranchId: String?,
    selectedBranch: BranchResponse?,
    actions: UserManagementBranchAdminActions,
) {
    BranchPicker(
        branches = branches,
        selectedBranchId = selectedBranchId,
        onBranchSelected = actions.onBranchSelected,
        onRetryBranches = actions.onRetryBranches,
        disabled = actions.pickerDisabled,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Branch administration",
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(
            onClick = actions.onCreateBranch,
            enabled = actions.createEnabled,
        ) {
            Text("Create branch")
        }
    }
    if (selectedBranch != null) {
        TextButton(
            onClick = actions.onAssign,
            enabled = actions.assignEnabled,
        ) {
            Text("Assign user to ${selectedBranch.name}")
        }
    }
    // Assignment error derivation lives here (not the Screen call site) for the #462
    // LongMethod burn — the Screen passes the raw states and this host owns the gate.
    val assignmentError =
        if (actions.assignmentResult is UiState.Error &&
            actions.removeAssignmentTarget == null &&
            !actions.showAssignDialog
        ) {
            actions.assignmentResult.message
        } else {
            null
        }
    if (assignmentError != null) {
        Text(
            text = assignmentError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Single user row with its row-error derivation hoisted out of [UserManagementScreen] for the
 * #462 LongMethod burn-down. Lives here (not same-file) because UserManagementScreen.kt sits
 * at the detekt file-function wall — a same-file helper trips TooManyFunctions. The
 * slot/swap errors for the selected branch stay claimed by the slot card (filter below
 * mirrors the Screen call site verbatim); callbacks ride [UserManagementUserRowActions] so
 * the signature stays LongParameterList-clean.
 */
@Composable
internal fun UserManagementUserRowHost(
    user: UserSummaryResponse,
    expanded: Boolean,
    actions: UserManagementUserRowActions,
) {
    UserRow(
        user = user,
        currentUserId = actions.currentUserId,
        expanded = expanded,
        onToggleExpanded = { actions.onToggleExpanded(user.id) },
        mutationsDisabled = actions.mutationsDisabled,
        onDeactivate = { actions.onDeactivate(user) },
        onReactivate = { actions.onReactivate(user.id) },
        onEditRoles = { actions.onEditRoles(user) },
        onEditSlot = { assignment -> actions.onEditSlot(user, assignment) },
        onRemoveAssignment = { assignment -> actions.onRemoveAssignment(user, assignment) },
        errors =
            actions.actionErrors
                .filterKeys {
                    !it.startsWith("swap:") &&
                        !it.startsWith("slot:${actions.selectedBranchId}:") &&
                        (
                            it.endsWith(":${user.id}") ||
                                user.assignments.any { assignment ->
                                    it ==
                                        "slot:${assignment.branchId}:${assignment.assignmentId}"
                                }
                        )
                }.values
                .toList(),
    )
}

/**
 * Slot-order card item hoisted out of [UserManagementScreen] for the #462 LongMethod burn-down.
 * Lives here (not same-file) because UserManagementScreen.kt sits at the detekt file-function
 * wall — a same-file helper trips TooManyFunctions. Plain @Composable (not LazyListScope) so
 * the Screen keeps the `item(key = "slot-order")` wrapper and this host owns only the
 * [UserSlotOrderList] call + the branch-scoped error filter + edit-target construction;
 * callbacks ride [UserManagementSlotOrderActions] so the signature stays
 * LongParameterList-clean.
 */
@Composable
internal fun UserManagementSlotOrderItem(
    selectedBranchId: String,
    branchName: String,
    rows: List<UserSlotRow>,
    mutationsDisabled: Boolean,
    actions: UserManagementSlotOrderActions,
) {
    UserSlotOrderList(
        branchName = branchName,
        rows = rows,
        mutationsDisabled = mutationsDisabled,
        callbacks =
            SlotOrderCallbacks(
                onSwap = actions.onSwap,
                onEditSlot = { row ->
                    actions.onEditSlot(
                        SlotEditTarget(
                            branchId = selectedBranchId,
                            branchName = branchName,
                            assignmentId = row.assignmentId,
                            displayName = row.displayName,
                            currentSlot = row.slot,
                        ),
                    )
                },
            ),
        errors =
            actions.actionErrors
                // Swap AND slot-edit errors for the selected branch surface
                // in the slot card (the trigger surface). Slot edits opened
                // from the card can fail on a COLLAPSED user row — the
                // row-level rendering was invisible there (pass-1 P1 HARD);
                // the row filter below excludes these keys so nothing
                // double-renders.
                .filterKeys {
                    it.startsWith("swap:$selectedBranchId:") ||
                        it.startsWith("slot:$selectedBranchId:")
                }.values
                .toList(),
    )
}

/**
 * "All users" list header hoisted out of [UserManagementScreen] for the #462 LongMethod
 * burn-down. Lives here (not same-file) because UserManagementScreen.kt sits at the detekt
 * file-function wall — a same-file helper trips TooManyFunctions. Plain content (not
 * LazyListScope) so the Screen keeps the `item(key = "users-header")` wrapper.
 */
@Composable
internal fun UserManagementListHeader() {
    Text(
        text = "All users",
        style = MaterialTheme.typography.titleMedium,
    )
}

/**
 * Reload-error strip hoisted out of [UserManagementScreen] for the #462 LongMethod burn-down.
 * Same wall rationale — plain content so the Screen keeps the `item(key =
 * "users-reload-error")` wrapper. Message + gate arrive as params; the Screen keeps the
 * explicit cast (delegated collectAsState vals never smart-cast).
 */
@Composable
internal fun UserManagementListErrorRow(
    message: String,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onRetry,
            enabled = retryEnabled,
        ) {
            Text("Retry")
        }
    }
}

/**
 * Empty-state content hoisted out of [UserManagementScreen] for the #462 LongMethod
 * burn-down. Same wall rationale. The Screen keeps the `item(key = "users-empty")` wrapper
 * with its `Box(Modifier.fillParentMaxSize())` (LazyItemScope-bound — no LazyListScope
 * precedent in codebase); this host owns only the message derivation + [EmptyState] call.
 */
@Composable
internal fun UserManagementEmptyContent(searchQuery: String) {
    EmptyState(
        message =
            if (searchQuery.isBlank()) {
                "No users"
            } else {
                "No users match \"$searchQuery\""
            },
    )
}

/**
 * Cold-start fallback hoisted out of [UserManagementScreen] for the #462 LongMethod
 * burn-down (status-when slimming). Lives here (not same-file) because
 * UserManagementScreen.kt sits at the detekt file-function wall — a same-file helper
 * trips TooManyFunctions. Owns the two non-list branches of the Screen's status when:
 * the users-leg ErrorCard and the cold-start spinner. The retry stays ungated (verbatim —
 * the reload-error strip inside the list owns the only gated retry).
 */
@Composable
internal fun UserManagementLoadFallback(
    users: UiState<List<UserSummaryResponse>>,
    onRetry: () -> Unit,
) {
    val errorState = users as? UiState.Error
    // LaunchedEffect form (not inline) so the sticky error state doesn't re-log
    // on every recomposition (e.g. search keystrokes) — same guard as BranchPicker.
    LaunchedEffect(errorState) {
        errorState?.let { logWarn("UserManagementScreen", "usersState=Error: ${it.message}") }
    }
    if (errorState != null) {
        ErrorCard(message = errorState.message, onRetry = onRetry)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

/**
 * Title/search + branch-admin block hoisted out of [UserManagementScreen] for the #462
 * LongMethod burn-down. Lives here (not same-file) because UserManagementScreen.kt sits
 * at the detekt file-function wall — a same-file helper trips TooManyFunctions; this is
 * the last safe slot (10/11). Forwards into [UserManagementHeader] +
 * [UserManagementBranchAdmin] so the Screen keeps one slim call (plus the trailing gap);
 * gates + setters ride [UserManagementTopSectionsActions] so the signature stays
 * LongParameterList-clean (5 params).
 */
@Composable
internal fun UserManagementTopSections(
    searchQuery: String,
    branches: UiState<List<BranchResponse>>,
    selectedBranchId: String?,
    selectedBranch: BranchResponse?,
    actions: UserManagementTopSectionsActions,
) {
    UserManagementHeader(
        searchQuery = searchQuery,
        onSearchChange = actions.onSearchChange,
        searchEnabled = actions.searchEnabled,
        actions =
            UserManagementHeaderActions(
                onInvite = actions.onInvite,
                onRefresh = actions.onRefresh,
                inviteEnabled = actions.inviteEnabled,
                refreshEnabled = actions.refreshEnabled,
            ),
    )
    Spacer(Modifier.size(Spacing.sm))
    UserManagementBranchAdmin(
        branches = branches,
        selectedBranchId = selectedBranchId,
        selectedBranch = selectedBranch,
        actions =
            UserManagementBranchAdminActions(
                onBranchSelected = actions.onBranchSelected,
                onRetryBranches = actions.onRetryBranches,
                onCreateBranch = actions.onCreateBranch,
                onAssign = actions.onAssign,
                pickerDisabled = actions.pickerDisabled,
                createEnabled = actions.createEnabled,
                assignEnabled = actions.assignEnabled,
                assignmentResult = actions.assignmentResult,
                removeAssignmentTarget = actions.removeAssignmentTarget,
                showAssignDialog = actions.showAssignDialog,
            ),
    )
    Spacer(Modifier.size(Spacing.sm))
}

/**
 * Row-actions construction hoisted out of [UserManagementScreen] for the #462 LongMethod
 * burn-down. Lives here (not same-file) because UserManagementScreen.kt sits at the
 * detekt file-function wall — a same-file helper trips TooManyFunctions. Owns the
 * multi-line toggle/target-construction bodies; the Screen passes single-line state
 * setters via [UserManagementUserRowCallbacks] so the call site stays lean (call-site
 * lambda bodies count toward the caller's LongMethod). Plain fun (no compose calls) with
 * 5 params so it stays LongParameterList-clean outside the LPL-excluded Screen file.
 */
internal fun userManagementUserRowActions(
    currentUserId: String?,
    mutationsDisabled: Boolean,
    selectedBranchId: String?,
    actionErrors: Map<String, String>,
    callbacks: UserManagementUserRowCallbacks,
): UserManagementUserRowActions =
    UserManagementUserRowActions(
        currentUserId = currentUserId,
        mutationsDisabled = mutationsDisabled,
        selectedBranchId = selectedBranchId,
        actionErrors = actionErrors,
        onToggleExpanded = { id ->
            val expanded = callbacks.expandedIds
            callbacks.onExpandedIdsChange(if (id in expanded) expanded - id else expanded + id)
        },
        onDeactivate = { user -> callbacks.onDeactivateTarget(user) },
        onReactivate = { id -> callbacks.onReactivate(id) },
        onEditRoles = { user -> callbacks.onRoleEditTarget(user) },
        onEditSlot = { user, assignment ->
            callbacks.onSlotEditTarget(
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
            callbacks.onResetAdministration()
            callbacks.onRemoveTarget(
                AssignmentRemovalTarget(
                    userId = user.id,
                    displayName = user.displayName,
                    assignment = assignment,
                ),
            )
        },
    )
