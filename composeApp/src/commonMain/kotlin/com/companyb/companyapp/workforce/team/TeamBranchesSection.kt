package com.companyb.companyapp.workforce.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.workforce.AssignmentResponse
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.SecondaryActionButton
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.workforce.branch.AssignmentRemovalTarget

/**
 * #681 — Branches tab: selected branch/name/type, member roster in Branch Slot
 * order, primary Assign user, secondary Create branch.
 *
 * Branch administration stays separate from daily attendance: presence/relief
 * work lives in Sessions → Team. Slot edits and removal are row-context actions
 * through the existing slot-order list (slot policy unchanged). Holders keep
 * the host LongParameterList-clean (the UserManagement actions-object precedent).
 */
internal data class BranchesTabState(
    val branches: UiState<List<BranchResponse>>,
    val selectedBranchId: String?,
    val selectedBranch: BranchResponse?,
    val slotRows: List<UserSlotRow>,
    val actionErrors: Map<String, String>,
    val assignmentError: String?,
    val mutationsDisabled: Boolean,
    val hasHeldUsers: Boolean,
)

internal data class BranchesTabCallbacks(
    val onBranchSelected: (String?) -> Unit,
    val onRetryBranches: () -> Unit,
    val onCreateBranch: () -> Unit,
    val onAssign: () -> Unit,
    val onSwap: (branchId: String, assignmentA: String, assignmentB: String) -> Unit,
    val onEditSlot: (SlotEditTarget) -> Unit,
)

@Composable
internal fun BranchesTab(
    state: BranchesTabState,
    callbacks: BranchesTabCallbacks,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        BranchPicker(
            branches = state.branches,
            selectedBranchId = state.selectedBranchId,
            onBranchSelected = callbacks.onBranchSelected,
            onRetryBranches = callbacks.onRetryBranches,
            disabled = state.mutationsDisabled,
        )
        if (state.selectedBranch != null) {
            Text(
                text = "${state.selectedBranch.name} (${state.selectedBranch.branchType})",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BranchesTabActions(state = state, callbacks = callbacks)
        state.assignmentError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.selectedBranch != null) {
            UserManagementSlotOrderItem(
                selectedBranchId = state.selectedBranch.id,
                branchName = state.selectedBranch.name,
                rows = state.slotRows,
                mutationsDisabled = state.mutationsDisabled,
                actions =
                    UserManagementSlotOrderActions(
                        actionErrors = state.actionErrors,
                        onSwap = { a, b -> callbacks.onSwap(state.selectedBranch.id, a, b) },
                        onEditSlot = callbacks.onEditSlot,
                    ),
            )
        } else {
            Text(
                text = "Select a branch to manage its roster",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun BranchesTabActions(
    state: BranchesTabState,
    callbacks: BranchesTabCallbacks,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Assign is the primary branch-membership action; Create branch is
        // secondary (the #681 hierarchy). Both gate on loading, Assign also
        // on a rendered list so a created row never hides behind an error.
        if (state.selectedBranch != null) {
            PrimaryActionButton(
                label = "Assign user to ${state.selectedBranch.name}",
                onClick = callbacks.onAssign,
                enabled = !state.mutationsDisabled && state.hasHeldUsers,
            )
        }
        SecondaryActionButton(
            label = "Create branch",
            onClick = callbacks.onCreateBranch,
            enabled = !state.mutationsDisabled,
        )
    }
}

/** Assignment-error derivation, matching the pre-#681 branch-admin host. */
internal fun branchesAssignmentError(
    assignmentResult: UiState<AssignmentResponse>,
    removeTarget: AssignmentRemovalTarget?,
    showAssignDialog: Boolean,
): String? =
    if (assignmentResult is UiState.Error && removeTarget == null && !showAssignDialog) {
        assignmentResult.message
    } else {
        null
    }
