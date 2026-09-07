package com.companyb.companyapp.workforce.attendance

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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.workforce.MemberAttendanceResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.workforce.attendance.AttendanceRosterViewModel
import com.companyb.companyapp.workforce.team.EditSlotDialog
import com.companyb.companyapp.workforce.team.SlotDialogOptions
import com.companyb.companyapp.workforce.team.SlotEditTarget

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

    LaunchedEffect(branchId) {
        viewModel.load(branchId)
    }

    if (isReliefUser) return
    val rows = freshest.orEmpty()
    // Silent exit while unauthorized or not yet loaded — the card only exists for members.
    if (rows.isEmpty()) return
    AttendanceRosterLoadedCard(
        viewModel = viewModel,
        branchId = branchId,
        branchName = branchName,
        currentUserId = currentUserId,
        data = RosterLoadedData(rosterState = rosterState, rows = rows),
    )
}

private data class RosterLoadedData(
    val rosterState: UiState<List<MemberAttendanceResponse>>,
    val rows: List<MemberAttendanceResponse>,
)

@Composable
private fun AttendanceRosterLoadedCard(
    viewModel: AttendanceRosterViewModel,
    branchId: String,
    branchName: String?,
    currentUserId: String?,
    data: RosterLoadedData,
) {
    val rosterState = data.rosterState
    val rows = data.rows
    val markState by viewModel.markResult.collectAsState()
    val slotState by viewModel.slotUpdate.collectAsState()
    val swapState by viewModel.swapUpdate.collectAsState()
    (rosterState as? UiState.Error)?.let { error ->
        RosterLoadError(message = error.message, onRetry = { viewModel.load(branchId) })
    }

    val selfSlot = rememberSaveable(branchId, saver = RosterSelfSlotStateSaver) { RosterSelfSlotState() }
    val mutationsDisabled = AttendanceRosterLogic.mutationsDisabled(rosterState, markState, slotState, swapState)
    val dialogDismissEnabled =
        AttendanceRosterLogic.dialogDismissEnabled(rosterState, markState, slotState, swapState)
    val swapCandidates = AttendanceRosterLogic.swapCandidates(rows, currentUserId)
    val selfSlotContext =
        RosterSelfSlotContext(
            branchId = branchId,
            branchName = branchName,
            currentUserId = currentUserId,
            swapCandidates = swapCandidates,
            state = selfSlot,
            resetSlotUpdate = viewModel::resetSlotUpdate,
            resetSwapUpdate = viewModel::resetSwapUpdate,
        )

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
                        selfService = row.ownRowAffordance(selfSlotContext),
                        onToggle = { viewModel.mark(branchId, row.userId, present = !row.present) },
                    )
                }
            }
        }
    }

    RosterActionErrors(listOf(markState, slotState, swapState))
    SelfSlotDialogs(selfSlot, viewModel, rows)
}

/**
 * Mutable self-service surface state (#416): which row's slot editor is open and whether the
 * swap picker is showing. Assignment IDs are captured when the picker opens so refreshed rows
 * cannot retarget the action. Saveable for process recreation.
 */
internal class RosterSelfSlotState {
    var editTarget: SlotEditTarget? by mutableStateOf(null)
    var swapTarget: RosterSwapTarget? by mutableStateOf(null)
}

private data class RosterSelfSlotContext(
    val branchId: String,
    val branchName: String?,
    val currentUserId: String?,
    val swapCandidates: List<MemberAttendanceResponse>,
    val state: RosterSelfSlotState,
    val resetSlotUpdate: () -> Unit,
    val resetSwapUpdate: () -> Unit,
)

internal data class RosterSwapTarget(
    val branchId: String,
    val ownAssignmentId: String,
    val candidateAssignmentIds: List<String>,
)

internal val RosterSelfSlotStateSaver =
    Saver<RosterSelfSlotState, List<String>>(
        save = { state ->
            val editTarget = state.editTarget
            val swapTarget = state.swapTarget
            listOf(
                if (editTarget == null) "0" else "1",
                editTarget?.branchId.orEmpty(),
                editTarget?.branchName.orEmpty(),
                editTarget?.assignmentId.orEmpty(),
                editTarget?.displayName.orEmpty(),
                editTarget?.currentSlot?.toString().orEmpty(),
                if (swapTarget == null) "0" else "1",
                swapTarget?.branchId.orEmpty(),
                swapTarget?.ownAssignmentId.orEmpty(),
                swapTarget?.candidateAssignmentIds?.joinToString(",").orEmpty(),
            )
        },
        restore = { values ->
            if (values.size != ROSTER_STATE_VALUE_COUNT) {
                RosterSelfSlotState()
            } else {
                RosterSelfSlotState().apply {
                    editTarget =
                        if (values[EDIT_PRESENT_INDEX] == "1") {
                            values[EDIT_SLOT_INDEX].toShortOrNull()?.let { slot ->
                                SlotEditTarget(
                                    branchId = values[EDIT_BRANCH_ID_INDEX],
                                    branchName = values[EDIT_BRANCH_NAME_INDEX],
                                    assignmentId = values[EDIT_ASSIGNMENT_ID_INDEX],
                                    displayName = values[EDIT_DISPLAY_NAME_INDEX],
                                    currentSlot = slot,
                                )
                            }
                        } else {
                            null
                        }
                    swapTarget =
                        if (
                            values[SWAP_PRESENT_INDEX] == "1" &&
                            values[SWAP_BRANCH_ID_INDEX].isNotEmpty() &&
                            values[SWAP_OWN_ASSIGNMENT_ID_INDEX].isNotEmpty()
                        ) {
                            RosterSwapTarget(
                                branchId = values[SWAP_BRANCH_ID_INDEX],
                                ownAssignmentId = values[SWAP_OWN_ASSIGNMENT_ID_INDEX],
                                candidateAssignmentIds =
                                    values[SWAP_CANDIDATE_IDS_INDEX].split(",").filter(String::isNotEmpty),
                            )
                        } else {
                            null
                        }
                }
            }
        },
    )

/**
 * The caller's own-row actions (#416); null keeps every other member's row read-only display
 * (the own-row-static rule's mirror).
 */
private fun MemberAttendanceResponse.ownRowAffordance(context: RosterSelfSlotContext): SelfSlotAffordance? =
    if (!AttendanceRosterLogic.canEditOwnSlot(this, context.currentUserId)) {
        null
    } else {
        SelfSlotAffordance(
            swapAvailable = context.swapCandidates.isNotEmpty(),
            onEditSlot = {
                context.resetSlotUpdate()
                context.state.editTarget =
                    SlotEditTarget(
                        branchId = context.branchId,
                        branchName = context.branchName?.ifEmpty { "Branch" } ?: "Branch",
                        assignmentId = this@ownRowAffordance.assignmentId,
                        displayName = displayName,
                        currentSlot = slot,
                    )
            },
            onSwap = {
                context.resetSwapUpdate()
                context.state.swapTarget =
                    RosterSwapTarget(
                        branchId = context.branchId,
                        ownAssignmentId = assignmentId,
                        candidateAssignmentIds = context.swapCandidates.map { it.assignmentId },
                    )
            },
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
) {
    // All three legs collected reactively — a .value read here can go stale when the parent
    // skips this call with unchanged parameters (the P4 non-reactive-read finding).
    val rosterState by viewModel.roster.collectAsState()
    val markState by viewModel.markResult.collectAsState()
    val slotState by viewModel.slotUpdate.collectAsState()
    val swapState by viewModel.swapUpdate.collectAsState()
    val mutationsDisabled = AttendanceRosterLogic.mutationsDisabled(rosterState, markState, slotState, swapState)
    val dialogDismissEnabled =
        AttendanceRosterLogic.dialogDismissEnabled(rosterState, markState, slotState, swapState)

    LaunchedEffect(slotState) {
        if (slotState is UiState.Success) {
            state.editTarget = null
        }
    }
    LaunchedEffect(swapState) {
        if (swapState is UiState.Success) {
            state.swapTarget = null
        }
    }

    state.editTarget?.let { target ->
        EditSlotDialog(
            target = target,
            options =
                SlotDialogOptions(
                    mutationsDisabled = mutationsDisabled,
                    errorMessage = (slotState as? UiState.Error)?.message,
                    dismissEnabled = dialogDismissEnabled,
                ),
            onDismiss = { state.editTarget = null },
            onSave = { slot ->
                viewModel.updateSlot(target.branchId, target.assignmentId, slot)
            },
        )
    }

    state.swapTarget?.let { target ->
        SwapSlotDialog(
            candidates = target.candidateAssignmentIds.mapNotNull { id -> rows.firstOrNull { it.assignmentId == id } },
            options =
                SlotDialogOptions(
                    mutationsDisabled = mutationsDisabled,
                    errorMessage = (swapState as? UiState.Error)?.message,
                    dismissEnabled = dialogDismissEnabled,
                ),
            onDismiss = { state.swapTarget = null },
            onPick = { candidate ->
                viewModel.swapSlots(target.branchId, target.ownAssignmentId, candidate.assignmentId)
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

@Composable
private fun RosterLoadError(
    message: String,
    onRetry: () -> Unit,
) {
    LaunchedEffect(message) {
        logWarn(TAG, "roster load error: $message")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text("Retry")
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
    options: SlotDialogOptions,
    onDismiss: () -> Unit,
    onPick: (MemberAttendanceResponse) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (options.dismissEnabled) onDismiss() },
        title = { Text("Swap my slot with…") },
        text = {
            Column {
                candidates.forEach { candidate ->
                    TextButton(enabled = !options.mutationsDisabled, onClick = { onPick(candidate) }) {
                        Text(AttendanceRosterLogic.swapCandidateLabel(candidate))
                    }
                }
                options.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = options.dismissEnabled) {
                Text("Cancel")
            }
        },
    )
}

private const val EDIT_PRESENT_INDEX = 0
private const val EDIT_BRANCH_ID_INDEX = 1
private const val EDIT_BRANCH_NAME_INDEX = 2
private const val EDIT_ASSIGNMENT_ID_INDEX = 3
private const val EDIT_DISPLAY_NAME_INDEX = 4
private const val EDIT_SLOT_INDEX = 5
private const val ROSTER_STATE_VALUE_COUNT = 10
private const val SWAP_PRESENT_INDEX = 6
private const val SWAP_BRANCH_ID_INDEX = 7
private const val SWAP_OWN_ASSIGNMENT_ID_INDEX = 8
private const val SWAP_CANDIDATE_IDS_INDEX = 9
