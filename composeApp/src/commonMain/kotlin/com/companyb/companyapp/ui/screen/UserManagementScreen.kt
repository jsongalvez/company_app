@file:Suppress("DEPRECATION")

package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.InviteMintRequest
import com.companyb.companyapp.dto.InviteMintResponse
import com.companyb.companyapp.dto.RoleResponse
import com.companyb.companyapp.dto.UserAssignmentResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
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
    // #350 — invite-mint + role-picker state.
    val mintState by viewModel.mintInviteResult.collectAsState()
    val rolesState by viewModel.roles.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
    var deactivateTarget by remember { mutableStateOf<UserSummaryResponse?>(null) }
    var slotEditTarget by remember { mutableStateOf<SlotEditTarget?>(null) }
    var showCreateUserDialog by rememberSaveable { mutableStateOf(false) }
    var roleEditTarget by remember { mutableStateOf<UserSummaryResponse?>(null) }

    LaunchedEffect(Unit) {
        logInfo("UserManagementScreen", "composable entered (first composition)")
        viewModel.loadUsers()
        viewModel.loadBranches()
        viewModel.loadRoles()
    }

    val loadedUsers = heldList.orEmpty()
    val filteredUsers = remember(loadedUsers, searchQuery) { filterUsers(loadedUsers, searchQuery) }
    val loadedBranches = (branches as? UiState.Success<List<BranchResponse>>)?.data.orEmpty()
    val slotRows =
        remember(loadedUsers, selectedBranchId) {
            selectedBranchId?.let { slotOrderForBranch(loadedUsers, it) }.orEmpty()
        }
    val selectedBranchName = loadedBranches.firstOrNull { it.id == selectedBranchId }?.name
    // Mutations disabled while one is in flight (ADR-0022) OR while a reload is in flight: the
    // keep-last gate renders live rows during Loading, and a mutation landing mid-load would be
    // clobbered by the load's pre-mutation snapshot (pass-1 HARD — the VM guard covers the
    // same-frame tap; this gate is the visible affordance).
    val mutationsDisabled = inFlight.isNotEmpty() || users is UiState.Loading

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
            Row {
                TextButton(
                    onClick = { showCreateUserDialog = true },
                    // Gated on a rendered list too: with nothing held (failed initial load) an
                    // appended created row would be invisible behind the ErrorCard — force the
                    // retry path instead (pass-4 P4).
                    enabled = !mutationsDisabled && heldList != null,
                ) {
                    Text("Invite user")
                }
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
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search users") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            // Typing against an Error state with nothing held does nothing visible (ErrorCard
            // renders instead of the list) — disable so the field doesn't look interactive
            // (pass-1 P4 SOFT). With held rows the keep-last gate renders the list, so the
            // client-side filter stays live over the mirror (#161).
            enabled = heldList != null || users !is UiState.Error,
        )

        Spacer(Modifier.size(Spacing.sm))

        BranchPicker(
            branches = branches,
            selectedBranchId = selectedBranchId,
            onBranchSelected = { selectedBranchId = it },
            onRetryBranches = { viewModel.loadBranches() },
        )

        Spacer(Modifier.size(Spacing.sm))

        when {
            heldList != null -> {
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
                                onEditRoles = { roleEditTarget = user },
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

            users is UiState.Error -> {
                val errorState = users as UiState.Error
                // LaunchedEffect form (not inline) so the sticky error state doesn't re-log
                // on every recomposition (e.g. search keystrokes) — same guard as BranchPicker.
                LaunchedEffect(errorState) {
                    logWarn("UserManagementScreen", "usersState=Error: ${errorState.message}")
                }
                ErrorCard(message = errorState.message, onRetry = { viewModel.loadUsers() })
            }

            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    deactivateTarget?.let { target ->
        DeactivateConfirmDialog(
            user = target,
            mutationsDisabled = mutationsDisabled,
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
            mutationsDisabled = mutationsDisabled,
            onDismiss = { slotEditTarget = null },
            onSave = { slot ->
                slotEditTarget = null
                viewModel.updateSlot(target.branchId, target.userId, slot)
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
                    showCreateUserDialog = false
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
            onRetryRoles = viewModel::loadRoles,
            onDismiss = { roleEditTarget = null },
            onSave = { selected ->
                roleEditTarget = null
                viewModel.replaceRoles(target.id, selected)
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
    onSwap: (userIdA: String, userIdB: String) -> Unit,
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
    onEditRoles: () -> Unit,
    onEditSlot: (UserAssignmentResponse) -> Unit,
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
                    // #345 — role names inline so admins spot unassigned/ONBOARDING users
                    // without expanding (the wire omits empty lists — the empty default renders
                    // the explicit "No roles" line).
                    Text(
                        text = if (user.roles.isEmpty()) "No roles" else user.roles.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    TextButton(onClick = onEditRoles, enabled = !mutationsDisabled) {
                        Text("Edit roles")
                    }
                    if (!isDeactivated && user.id != currentUserId) {
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
private fun StatusBadge(status: UserStatus) {
    val label =
        when (status) {
            UserStatus.ACTIVE -> "ACTIVE"
            UserStatus.INACTIVE -> "INACTIVE"
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
    mutationsDisabled: Boolean,
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
            // Gated like the row actions (pass-2 HARD): the dialog can be open when a load is in
            // flight (same-frame refresh-tap + row-tap slip), and the VM guard would swallow the
            // confirm silently — the gate makes the window visible instead. The explicit error
            // color must yield while disabled or the button wouldn't look dead (pass-3 SOFT).
            TextButton(
                onClick = onConfirm,
                enabled = !mutationsDisabled,
            ) {
                Text(
                    text = "Deactivate",
                    color =
                        if (mutationsDisabled) {
                            // M3's disabledContentColor (dimmed) — an explicit error color would
                            // keep the dead button vivid red (pass-4 SOFT).
                            Color.Unspecified
                        } else {
                            MaterialTheme.colorScheme.error
                        },
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
    mutationsDisabled: Boolean,
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
                    // Gated with the Save button (pass-3 SOFT): typing into a field whose action
                    // is dead mid-load has no affordance — the field goes inert with it.
                    enabled = !mutationsDisabled,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val error = slotInputError(input)
                    if (error == null) {
                        onSave(parseSlotInput(input)!!)
                    } else {
                        inputError = error
                    }
                },
                // Gated like the row actions (pass-2 HARD — see DeactivateConfirmDialog).
                enabled = !mutationsDisabled,
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

/**
 * #350 — invite-mint dialog (the #345 create-user shape minus the password field): username,
 * email, display name; the backend owns email policy and duplicate detection so client
 * validation stays presence-only and 400/409 bodies render inline.
 *
 * A Success does NOT auto-close — the single-use code is the only deliverable, so the dialog
 * flips to a result panel (code + expiry + copy) until the admin explicitly closes it
 * ([UserViewModel.dismissInviteResult] resets the flow for the next open). Mid-flight dismissal
 * stays blocked (an orphaned POST would still mint the account).
 */
@Composable
private fun InviteMintDialog(
    mintState: UiState<InviteMintResponse>,
    onMint: (InviteMintRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    val inFlight = mintState is UiState.Loading
    val complete = listOf(username, email, displayName).all { it.isNotBlank() }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite user") },
        text = {
            Column {
                when (val state = mintState) {
                    is UiState.Success -> {
                        LaunchedEffect(state) {
                            logInfo("UserManagementScreen", "mintState=Success; code ready to copy")
                        }
                        Text(
                            text = "Share this single-use code. It expires ${state.data.expiresAt}.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = state.data.inviteCode,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(Spacing.md),
                            )
                        }
                        Spacer(Modifier.size(Spacing.sm))
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(state.data.inviteCode))
                        }) {
                            Text("Copy code")
                        }
                    }

                    else -> {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            enabled = !inFlight,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            enabled = !inFlight,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display name") },
                            singleLine = true,
                            enabled = !inFlight,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        (state as? UiState.Error)?.let { errorState ->
                            LaunchedEffect(errorState) {
                                // Sticky branch — log once per state, not per recomposition (the
                                // usersState=Error guard shape).
                                logWarn("UserManagementScreen", "mintState=Error: ${errorState.message}")
                            }
                            Text(
                                text = errorState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (mintState) {
                is UiState.Success -> {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }

                else -> {
                    TextButton(
                        onClick = {
                            onMint(
                                InviteMintRequest(
                                    username = username.trim(),
                                    email = email.trim(),
                                    displayName = displayName.trim(),
                                ),
                            )
                        },
                        enabled = complete && !inFlight,
                    ) {
                        Text(if (inFlight) "Minting…" else "Mint invite")
                    }
                }
            }
        },
        dismissButton = {
            if (mintState is UiState.Success) {
                Unit
            } else {
                TextButton(onClick = onDismiss, enabled = !inFlight) {
                    Text("Cancel")
                }
            }
        },
    )
}

/**
 * #345 — full-replace role editor backed by `GET /api/roles` (seeded bundles only — SUPERUSER
 * absent by design). Loading/error render in place with tap-to-retry (the BranchPicker shape);
 * Save issues the PUT only over a loaded bundle list. The checkbox set starts from the row's
 * current roles intersected with what the picker offers.
 */
@Composable
private fun RoleEditDialog(
    user: UserSummaryResponse,
    rolesState: UiState<List<RoleResponse>>,
    mutationsDisabled: Boolean,
    onRetryRoles: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val options = (rolesState as? UiState.Success<List<RoleResponse>>)?.data.orEmpty()
    var selected by remember(user.id, rolesState) {
        mutableStateOf(
            user.roles.filter { name -> options.any { it.name == name } }.toSet(),
        )
    }
    // Re-arm ONLY an untouched source: Idle means never loaded (first open). Auto-refiring on
    // Loading/Error would loop requests (this effect restarts on every state change); Error
    // retries through the manual tap-to-retry affordance instead.
    LaunchedEffect(rolesState) {
        if (rolesState is UiState.Idle) onRetryRoles()
    }

    val rolesLoading = rolesState is UiState.Loading || rolesState is UiState.Idle

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit roles — ${user.displayName}") },
        text = {
            Column {
                when {
                    rolesState is UiState.Error -> {
                        val errorState = rolesState
                        LaunchedEffect(errorState) {
                            logWarn("UserManagementScreen", "rolesState=Error: ${errorState.message}")
                        }
                        Text(
                            text = "Roles unavailable — tap to retry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onRetryRoles)
                                    .padding(vertical = Spacing.sm),
                        )
                    }

                    rolesLoading -> {
                        Text(
                            text = "Loading roles…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.sm),
                        )
                    }

                    options.isEmpty() -> {
                        Text(
                            text = "No roles configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        options.forEach { role ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !mutationsDisabled) {
                                            selected =
                                                if (role.name in selected) {
                                                    selected - role.name
                                                } else {
                                                    selected + role.name
                                                }
                                        },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = role.name in selected,
                                    onCheckedChange = null,
                                    enabled = !mutationsDisabled,
                                )
                                Text(
                                    text = role.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selected.toList()) },
                enabled = !mutationsDisabled && options.isNotEmpty(),
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
