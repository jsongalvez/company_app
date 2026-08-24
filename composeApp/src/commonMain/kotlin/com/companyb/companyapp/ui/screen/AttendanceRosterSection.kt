package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.MemberAttendanceResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.AttendanceRosterViewModel
import com.companyb.companyapp.viewmodel.UiState

private const val TAG = "AttendanceRosterSection"

/**
 * #404 — the member-marked attendance surface on the session dashboard (both hosts): the
 * branch's home-member roster in Branch Slot order, where members correct others'
 * present/absent (self attendance stays the drawer's Clock in / Clock out). The backend's
 * membership gate is authoritative — a 403 (or any load failure with nothing loaded yet)
 * renders nothing, so relief users and outsiders never see the card.
 *
 * #416 — the caller's own row grows a self-service overflow menu ("Set my slot" via the
 * shared EditSlotDialog, "Swap…" against another roster member); every other member's slot
 * stays read-only display (their present/absent toggles are #404's separate affordance).
 * Zero contract change: both legs already exist for participants.
 */
@Composable
fun AttendanceRosterCard(
    viewModel: AttendanceRosterViewModel,
    branchId: String,
    branchName: String?,
    currentUserId: String?,
    isReliefUser: Boolean,
) {
    val freshest by viewModel.freshestRoster.collectAsState()
    val rosterState by viewModel.roster.collectAsState()
    val markState by viewModel.markResult.collectAsState()
    val slotState by viewModel.slotUpdate.collectAsState()
    val swapState by viewModel.swapUpdate.collectAsState()

    LaunchedEffect(branchId) {
        viewModel.load(branchId)
    }

    if (isReliefUser) return
    val rows = freshest.orEmpty()
    // Silent exit while unauthorized or not yet loaded — the card only exists for members.
    if (rows.isEmpty()) return

    val selfSlot = remember { RosterSelfSlotState() }
    // One in-flight notion for the whole card: any busy leg — including the post-mutation
    // roster reload (its Loading rides [AttendanceRosterViewModel.roster]) — disables every
    // sibling action, so a stale-row second swap can never dispatch behind a landing refresh.
    val mutationsDisabled =
        rosterState is UiState.Loading ||
            markState is UiState.Loading ||
            slotState is UiState.Loading ||
            swapState is UiState.Loading
    val swapAvailable = AttendanceRosterLogic.swapCandidates(rows, currentUserId).isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
        SectionHeader(rows)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            shape = RoundedCornerShape(CornerRadius.lg),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                rows.forEach { row ->
                    RosterRow(
                        row = row,
                        canToggle = AttendanceRosterLogic.canToggle(row, currentUserId),
                        busy = mutationsDisabled,
                        selfService =
                            row.ownRowAffordance(branchId, branchName, currentUserId, swapAvailable, selfSlot),
                        onToggle = { viewModel.mark(branchId, row.userId, present = !row.present) },
                    )
                }
            }
        }
    }

    RosterActionErrors(listOf(markState, slotState, swapState))
    SelfSlotDialogs(selfSlot, viewModel, rows, currentUserId, branchId)
}

/**
 * Mutable self-service surface state (#416): which row's slot editor is open and whether the
 * swap picker is showing. Remembered per card instance; leaving composition discards it.
 */
internal class RosterSelfSlotState {
    var editTarget: SlotEditTarget? by mutableStateOf(null)
    var swapOpen: Boolean by mutableStateOf(false)
}

/**
 * The caller's own-row actions (#416); null keeps every other member's row read-only display
 * (the own-row-static rule's mirror).
 */
private fun MemberAttendanceResponse.ownRowAffordance(
    branchId: String,
    branchName: String?,
    currentUserId: String?,
    swapAvailable: Boolean,
    state: RosterSelfSlotState,
): SelfSlotAffordance? =
    if (!AttendanceRosterLogic.canEditOwnSlot(this, currentUserId)) {
        null
    } else {
        SelfSlotAffordance(
            swapAvailable = swapAvailable,
            onEditSlot = {
                state.editTarget =
                    SlotEditTarget(
                        branchId = branchId,
                        branchName = branchName?.ifEmpty { "Branch" } ?: "Branch",
                        userId = userId,
                        displayName = displayName,
                        currentSlot = slot,
                    )
            },
            onSwap = { state.swapOpen = true },
        )
    }

/** Surface action failures once each, inline (the relief-access action-error shape). */
@Composable
private fun RosterActionErrors(states: List<UiState<*>>) {
    states.filterIsInstance<UiState.Error>().forEach { error ->
        logWarn(TAG, "roster action error: ${error.message}")
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

/** #416 — the self-service dialog pair, hosted outside the roster column so neither dialog inherits its padding. */
@Composable
private fun SelfSlotDialogs(
    state: RosterSelfSlotState,
    viewModel: AttendanceRosterViewModel,
    rows: List<MemberAttendanceResponse>,
    currentUserId: String?,
    branchId: String,
) {
    // All three legs collected reactively — a .value read here can go stale when the parent
    // skips this call with unchanged parameters (the P4 non-reactive-read finding).
    val rosterState by viewModel.roster.collectAsState()
    val markState by viewModel.markResult.collectAsState()
    val slotState by viewModel.slotUpdate.collectAsState()
    val swapState by viewModel.swapUpdate.collectAsState()
    val mutationsDisabled =
        rosterState is UiState.Loading ||
            markState is UiState.Loading ||
            slotState is UiState.Loading ||
            swapState is UiState.Loading

    state.editTarget?.let { target ->
        EditSlotDialog(
            target = target,
            mutationsDisabled = mutationsDisabled,
            onDismiss = { state.editTarget = null },
            onSave = { slot ->
                // Save closes immediately (the Profile/User Management precedent); failure
                // surfaces inline and success refreshes the roster.
                state.editTarget = null
                viewModel.updateSlot(target.branchId, target.userId, slot)
            },
        )
    }

    if (state.swapOpen) {
        SwapSlotDialog(
            candidates = AttendanceRosterLogic.swapCandidates(rows, currentUserId),
            mutationsDisabled = mutationsDisabled,
            onDismiss = { state.swapOpen = false },
            onPick = { candidate ->
                state.swapOpen = false
                currentUserId?.let { viewModel.swapSlots(branchId, it, candidate.userId) }
            },
        )
    }
}

/** Bundle of the caller's own-row actions (#416); null keeps every other row read-only. */
internal class SelfSlotAffordance(
    val swapAvailable: Boolean,
    val onEditSlot: () -> Unit,
    val onSwap: () -> Unit,
)

@Composable
private fun SectionHeader(rows: List<MemberAttendanceResponse>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "Attendance",
            style = MaterialTheme.typography.labelSmall,
            color = InkSubtle,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(
            text = AttendanceRosterLogic.summaryLine(rows).orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = InkSubtle,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
    }
}

@Composable
private fun RosterRow(
    row: MemberAttendanceResponse,
    canToggle: Boolean,
    busy: Boolean,
    selfService: SelfSlotAffordance?,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = row.displayName, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canToggle) {
                TextButton(enabled = !busy, onClick = onToggle) {
                    Text(AttendanceRosterLogic.toggleLabel(row))
                }
            } else {
                Text(
                    text = if (row.present) "Present" else "Absent",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSubtle,
                )
            }
            OwnSlotMenu(selfService, busy)
        }
    }
}

/** The caller's own-row overflow affordance (#416) — absent on every other member's row. */
@Composable
private fun OwnSlotMenu(
    selfService: SelfSlotAffordance?,
    busy: Boolean,
) {
    if (selfService == null) return
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(enabled = !busy, onClick = { open = true }) {
            Text(
                text = "⋯",
                modifier = Modifier.semantics { contentDescription = "My slot actions" },
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Set my slot") },
                enabled = !busy,
                onClick = {
                    open = false
                    selfService.onEditSlot()
                },
            )
            // No candidates → no entry (nothing to trade with), not a dead item.
            if (selfService.swapAvailable) {
                DropdownMenuItem(
                    text = { Text("Swap…") },
                    enabled = !busy,
                    onClick = {
                        open = false
                        selfService.onSwap()
                    },
                )
            }
        }
    }
}

/** #416 — pick the member whose slot trades places with mine (the participant rule covers me). */
@Composable
private fun SwapSlotDialog(
    candidates: List<MemberAttendanceResponse>,
    mutationsDisabled: Boolean,
    onDismiss: () -> Unit,
    onPick: (MemberAttendanceResponse) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap my slot with…") },
        text = {
            Column {
                candidates.forEach { candidate ->
                    TextButton(enabled = !mutationsDisabled, onClick = { onPick(candidate) }) {
                        Text(AttendanceRosterLogic.swapCandidateLabel(candidate))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
