package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.UserAssignmentResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.USER_STATUS_ACTIVE
import com.companyb.companyapp.viewmodel.USER_STATUS_INACTIVE
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.UserSlotRow
import com.companyb.companyapp.viewmodel.UserViewModel
import com.companyb.companyapp.viewmodel.filterUsers
import com.companyb.companyapp.viewmodel.parseSlotInput
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
    currentUserId: String?,
) {
    val users by viewModel.users.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val inFlight by viewModel.inFlight.collectAsState()
    val actionErrors by viewModel.actionErrors.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
    var deactivateTarget by remember { mutableStateOf<UserSummaryResponse?>(null) }
    var slotEditTarget by remember { mutableStateOf<SlotEditTarget?>(null) }

    LaunchedEffect(Unit) {
        logInfo("UserManagementScreen", "composable entered (first composition)")
        viewModel.loadUsers()
        viewModel.loadBranches()
    }

    val loadedUsers = (users as? UiState.Success<List<UserSummaryResponse>>)?.data.orEmpty()
    val filteredUsers = remember(loadedUsers, searchQuery) { filterUsers(loadedUsers, searchQuery) }
    val loadedBranches = (branches as? UiState.Success<List<BranchResponse>>)?.data.orEmpty()
    val slotRows =
        remember(loadedUsers, selectedBranchId) {
            selectedBranchId?.let { slotOrderForBranch(loadedUsers, it) }.orEmpty()
        }
    val selectedBranchName = loadedBranches.firstOrNull { it.id == selectedBranchId }?.name
    val mutationsDisabled = inFlight.isNotEmpty()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
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
            TextButton(
                onClick = {
                    viewModel.loadUsers()
                    viewModel.loadBranches()
                },
                // A refresh landing mid-mutation lets the mutation's in-place transform re-apply
                // to the fresh list (swap would double-apply — pass-1 P2/P4 HARD). Belt: the
                // button gate here; suspenders: UserViewModel.loadUsers also skips while any
                // mutation is in flight (covers non-click triggers like LaunchedEffect refires).
                enabled = !mutationsDisabled,
            ) {
                Text("Refresh")
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search users") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            // Typing against an Error state does nothing visible (ErrorCard renders instead of
            // the list) — disable so the field doesn't look interactive (pass-1 P4 SOFT).
            enabled = users !is UiState.Error,
        )

        Spacer(Modifier.size(Spacing.sm))

        BranchPicker(
            branches = branches,
            selectedBranchId = selectedBranchId,
            onBranchSelected = { selectedBranchId = it },
            onRetryBranches = { viewModel.loadBranches() },
        )

        Spacer(Modifier.size(Spacing.sm))

        when (val state = users) {
            is UiState.Idle, is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                // LaunchedEffect form (not inline) so the sticky error state doesn't re-log on
                // every recomposition (e.g. search keystrokes) — same guard as BranchPicker.
                LaunchedEffect(state) {
                    logWarn("UserManagementScreen", "usersState=Error: ${state.message}")
                }
                ErrorCard(message = state.message, onRetry = { viewModel.loadUsers() })
            }

            is UiState.Success -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (selectedBranchId != null) {
                        item(key = "slot-order") {
                            UserSlotOrderList(
                                branchName = selectedBranchName ?: "",
                                rows = slotRows,
                                mutationsDisabled = mutationsDisabled,
                                onSwap = { a, b -> viewModel.swapSlots(selectedBranchId!!, a, b) },
                                onEditSlot = { row ->
                                    slotEditTarget =
                                        SlotEditTarget(
                                            branchId = selectedBranchId!!,
                                            branchName = selectedBranchName ?: "",
                                            userId = row.userId,
                                            displayName = row.displayName,
                                            currentSlot = row.slot,
                                        )
                                },
                                errors =
                                    actionErrors
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
                    }

                    item(key = "users-header") {
                        Text(
                            text = "All users",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    if (filteredUsers.isEmpty()) {
                        item(key = "users-empty") {
                            Box(Modifier.fillParentMaxSize()) {
                                EmptyState(
                                    message =
                                        if (searchQuery.isBlank()) {
                                            "No users"
                                        } else {
                                            "No users match \"$searchQuery\""
                                        },
                                )
                            }
                        }
                    } else {
                        items(filteredUsers, key = { it.id }) { user ->
                            UserRow(
                                user = user,
                                currentUserId = currentUserId,
                                expanded = user.id in expandedIds,
                                onToggleExpanded = {
                                    expandedIds =
                                        if (user.id in expandedIds) expandedIds - user.id else expandedIds + user.id
                                },
                                mutationsDisabled = mutationsDisabled,
                                onDeactivate = { deactivateTarget = user },
                                onReactivate = { viewModel.reactivateUser(user.id) },
                                onEditSlot = { assignment ->
                                    slotEditTarget =
                                        SlotEditTarget(
                                            branchId = assignment.branchId,
                                            branchName = assignment.branchName,
                                            userId = user.id,
                                            displayName = user.displayName,
                                            currentSlot = assignment.slot,
                                        )
                                },
                                errors =
                                    actionErrors
                                        .filterKeys {
                                            !it.startsWith("swap:") &&
                                                // Slot errors for the selected branch are claimed
                                                // by the slot card (see the card's filter above).
                                                !it.startsWith("slot:$selectedBranchId:") &&
                                                it.endsWith(":${user.id}")
                                        }.values
                                        .toList(),
                            )
                        }
                    }
                }
            }
        }
    }

    deactivateTarget?.let { target ->
        DeactivateConfirmDialog(
            user = target,
            onDismiss = { deactivateTarget = null },
            onConfirm = {
                deactivateTarget = null
                viewModel.deactivateUser(target.id)
            },
        )
    }

    slotEditTarget?.let { target ->
        EditSlotDialog(
            target = target,
            onDismiss = { slotEditTarget = null },
            onSave = { slot ->
                slotEditTarget = null
                viewModel.updateSlot(target.branchId, target.userId, slot)
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
expect fun UserSlotOrderList(
    branchName: String,
    rows: List<UserSlotRow>,
    mutationsDisabled: Boolean,
    onSwap: (userIdA: String, userIdB: String) -> Unit,
    onEditSlot: (row: UserSlotRow) -> Unit,
    errors: List<String>,
)

/**
 * Shared slot-order card chrome (#135 D4): Surface + "Slot order — <branch>" header + empty
 * state + trailing inline errors. Platform actuals render their rows through [content] (desktop
 * swap arrows / android tap-to-edit — the #95 responsive split).
 */
@Composable
internal fun UserSlotOrderCard(
    branchName: String,
    isEmpty: Boolean,
    errors: List<String>,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = "Slot order — $branchName",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(Spacing.sm))
            if (isEmpty) {
                Text(
                    text = "No assigned users at this branch",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                content()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchPicker(
    branches: UiState<List<BranchResponse>>,
    selectedBranchId: String?,
    onBranchSelected: (String?) -> Unit,
    onRetryBranches: () -> Unit,
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
            if (isError || isIdle) {
                onRetryBranches()
            } else if (!isLoading) {
                expanded = !expanded
            }
        },
        modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            enabled = !isLoading && !isIdle,
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

@Composable
private fun UserRow(
    user: UserSummaryResponse,
    currentUserId: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    mutationsDisabled: Boolean,
    onDeactivate: () -> Unit,
    onReactivate: () -> Unit,
    onEditSlot: (UserAssignmentResponse) -> Unit,
    errors: List<String>,
) {
    val isDeactivated = user.status == USER_STATUS_INACTIVE
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
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = user.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    user.deactivatedAt?.let {
                        Text(
                            text = "deactivated ${formatRelativeTimestamp(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                StatusBadge(status = user.status)
            }

            if (expanded) {
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
                        }
                    }
                }

                if (user.id == currentUserId) {
                    Text(
                        text = "You can't deactivate your own account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    if (!isDeactivated && user.id != currentUserId) {
                        TextButton(onClick = onDeactivate, enabled = !mutationsDisabled) {
                            Text(
                                text = "Deactivate",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (isDeactivated) {
                        TextButton(onClick = onReactivate, enabled = !mutationsDisabled) {
                            Text("Reactivate")
                        }
                    }
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
private fun StatusBadge(status: String) {
    // Unknown statuses render raw (backend enum is ACTIVE/INACTIVE today; a future status must
    // not masquerade as INACTIVE — pass-1 P2 SOFT).
    val label =
        when (status) {
            USER_STATUS_ACTIVE -> "ACTIVE"
            USER_STATUS_INACTIVE -> "INACTIVE"
            else -> status
        }
    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        color = MaterialTheme.colorScheme.secondary,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Unknown statuses render raw — a long value must not inflate the clickable row
            // (pass-2 P4 SOFT).
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

/** D3 — deactivate confirmation stating the consequences; reactivate stays a direct action. */
@Composable
private fun DeactivateConfirmDialog(
    user: UserSummaryResponse,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deactivate ${user.displayName}?") },
        text = {
            Column {
                Text(
                    text =
                        "Their login is blocked immediately (active tokens are killed) and " +
                            "all capabilities are removed. Records and branch assignments are kept. " +
                            "This can be undone with Reactivate.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Deactivate",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

data class SlotEditTarget(
    val branchId: String,
    val branchName: String,
    val userId: String,
    val displayName: String,
    val currentSlot: Short,
)

/**
 * D4 — tap-to-edit slot number (mobile primary; manual number fallback on desktop). Client-side
 * validation mirrors the backend's 400 ("Slot must be 1 or greater") so an invalid input never
 * leaves the dialog.
 */
@Composable
private fun EditSlotDialog(
    target: SlotEditTarget,
    onDismiss: () -> Unit,
    onSave: (Short) -> Unit,
) {
    var input by remember { mutableStateOf(target.currentSlot.toString()) }
    var inputError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit slot — ${target.displayName}") },
        text = {
            Column {
                Text(
                    text = target.branchName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(Spacing.sm))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Slot number") },
                    singleLine = true,
                    isError = inputError != null,
                    supportingText = { inputError?.let { Text(it) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val slot = parseSlotInput(input)
                    if (slot == null) {
                        // Distinguish the two rejection classes: parseSlotInput returns null both
                        // for invalid input and for values beyond Short (the shared DTO + backend
                        // column are SMALLINT) — one message would lie for the other (pass-1 P2).
                        // toLongOrNull (not toIntOrNull): a 20-digit number must classify as
                        // too-large, not as invalid (pass-2 P1/P4).
                        val numeric =
                            input.trim().toLongOrNull()?.let { it >= 1 } == true
                        inputError =
                            if (numeric) {
                                "Slot number too large (max 32767)"
                            } else {
                                "Slot must be 1 or greater"
                            }
                    } else {
                        onSave(slot)
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// Shared across common + both platform actuals (#135 D2 dimmed-rows treatment).
internal const val DEACTIVATED_ROW_ALPHA = 0.55f
