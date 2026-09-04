package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.UserSummaryResponse
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
    if (actions.assignmentError != null) {
        Text(
            text = actions.assignmentError,
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
