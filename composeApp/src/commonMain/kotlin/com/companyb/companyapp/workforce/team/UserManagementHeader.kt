package com.companyb.companyapp.workforce.team

import androidx.compose.runtime.Composable

/**
 * Slot-order card item of the Branches tab, hoisted out of [UserManagementScreen] for the #462
 * LongMethod burn-down. Plain @Composable (not LazyListScope) so the caller keeps the
 * `item(key = "slot-order")` wrapper and this host owns only the [UserSlotOrderList] call +
 * the branch-scoped error filter + edit-target construction; callbacks ride
 * [UserManagementSlotOrderActions] so the signature stays LongParameterList-clean.
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
                // Swap AND slot-edit errors for the selected branch surface in
                // the slot card (the trigger surface). Person-scoped errors for
                // the same slot keys also render on that person's row/detail on
                // the People tab — the tabs are exclusive, so both never show
                // at once; an open edit dialog additionally carries its own
                // inline error (the slot-dialog shape).
                .filterKeys {
                    it.startsWith("swap:$selectedBranchId:") ||
                        it.startsWith("slot:$selectedBranchId:")
                }.values
                .toList(),
    )
}
