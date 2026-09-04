@file:Suppress("DEPRECATION")

package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.AssignmentResponse
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.InviteMintRequest
import com.companyb.companyapp.dto.InviteMintResponse
import com.companyb.companyapp.dto.RoleResponse
import com.companyb.companyapp.dto.UserAssignmentResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.UserSlotRow
import com.companyb.companyapp.viewmodel.parseSlotInput
import com.companyb.companyapp.viewmodel.slotInputError

/**
 * Dialogs of the User Management screen (#345/#350), split out of UserManagementScreen.kt to
 * keep both files under the detekt file-function budget — plus the shared slot-order card
 * chrome and the user-row header (same #462 budget split; both files sit at the wall).
 * Same package, internal visibility.
 *
 * The [LocalClipboardManager] copy affordance rides the deprecated-but-common clipboard API
 * until the project adopts the suspend [androidx.compose.ui.platform.LocalClipboard] migration
 * wholesale (same debt as UserManagementScreen.kt's file suppress).
 */
internal data class SlotEditTarget(
    val branchId: String,
    val branchName: String,
    val assignmentId: String,
    val displayName: String,
    val currentSlot: Short,
)

internal data class SlotDialogOptions(
    val mutationsDisabled: Boolean,
    val errorMessage: String? = null,
    val dismissEnabled: Boolean = !mutationsDisabled,
)

/**
 * Callbacks + gates for [UserManagementHeader] (the header lives in its own file under the
 * #462 file-function budget split; the actions object keeps its signature
 * LongParameterList-clean — RoleEditActions precedent).
 */
internal data class UserManagementHeaderActions(
    val onInvite: () -> Unit,
    val onRefresh: () -> Unit,
    val inviteEnabled: Boolean,
    val refreshEnabled: Boolean,
)

/**
 * Callbacks + gates for [UserManagementBranchAdmin] (#462 LongMethod burn — 2nd fn in
 * UserManagementHeader.kt, which has fresh file-function budget while UserManagementScreen.kt
 * sits at the wall; actions object keeps the host LongParameterList-clean).
 */
internal data class UserManagementBranchAdminActions(
    val onBranchSelected: (String?) -> Unit,
    val onRetryBranches: () -> Unit,
    val onCreateBranch: () -> Unit,
    val onAssign: () -> Unit,
    val pickerDisabled: Boolean,
    val createEnabled: Boolean,
    val assignEnabled: Boolean,
    val assignmentResult: UiState<AssignmentResponse>,
    val removeAssignmentTarget: AssignmentRemovalTarget?,
    val showAssignDialog: Boolean,
)

/**
 * Callbacks + gates for [UserManagementUserRowHost] (#462 LongMethod burn — 3rd fn in
 * UserManagementHeader.kt, which has fresh file-function budget while UserManagementScreen.kt
 * sits at the wall; actions object keeps the host LongParameterList-clean). The row-error
 * filter derives inside the host so the Screen call site stays lean.
 */
internal data class UserManagementUserRowActions(
    val currentUserId: String?,
    val mutationsDisabled: Boolean,
    val selectedBranchId: String?,
    val actionErrors: Map<String, String>,
    val onToggleExpanded: (String) -> Unit,
    val onDeactivate: (UserSummaryResponse) -> Unit,
    val onReactivate: (String) -> Unit,
    val onEditRoles: (UserSummaryResponse) -> Unit,
    val onEditSlot: (UserSummaryResponse, UserAssignmentResponse) -> Unit,
    val onRemoveAssignment: (UserSummaryResponse, UserAssignmentResponse) -> Unit,
)

/**
 * Single-line state setters behind the row-actions construction (#462 LongMethod burn —
 * the Screen builds this inline while [userManagementUserRowActions] owns the multi-line
 * toggle/target-construction bodies; data class so LongParameterList/TooManyFunctions-free).
 */
internal data class UserManagementUserRowCallbacks(
    val expandedIds: Set<String>,
    val onExpandedIdsChange: (Set<String>) -> Unit,
    val onDeactivateTarget: (UserSummaryResponse?) -> Unit,
    val onRoleEditTarget: (UserSummaryResponse?) -> Unit,
    val onSlotEditTarget: (SlotEditTarget?) -> Unit,
    val onRemoveTarget: (AssignmentRemovalTarget?) -> Unit,
    val onReactivate: (String) -> Unit,
    val onResetAdministration: () -> Unit,
)

/**
 * Callbacks + gates for [UserManagementSlotOrderItem] (#462 LongMethod burn — 4th fn in
 * UserManagementHeader.kt, which has fresh file-function budget while UserManagementScreen.kt
 * sits at the wall; actions object keeps the host LongParameterList-clean). The slot-card
 * error filter + edit-target construction derive inside the host so the Screen call site
 * stays lean.
 */
internal data class UserManagementSlotOrderActions(
    val actionErrors: Map<String, String>,
    val onSwap: (String, String) -> Unit,
    val onEditSlot: (SlotEditTarget) -> Unit,
)

/**
 * Callbacks + gates for [UserManagementTopSections] (#462 LongMethod burn — 10th fn in
 * UserManagementHeader.kt, the last safe slot while UserManagementScreen.kt sits at the
 * wall; actions object keeps the host LongParameterList-clean).
 */
internal data class UserManagementTopSectionsActions(
    val searchEnabled: Boolean,
    val onSearchChange: (String) -> Unit,
    val onInvite: () -> Unit,
    val onRefresh: () -> Unit,
    val inviteEnabled: Boolean,
    val refreshEnabled: Boolean,
    val onBranchSelected: (String?) -> Unit,
    val onRetryBranches: () -> Unit,
    val onCreateBranch: () -> Unit,
    val onAssign: () -> Unit,
    val pickerDisabled: Boolean,
    val createEnabled: Boolean,
    val assignEnabled: Boolean,
    val assignmentResult: UiState<AssignmentResponse>,
    val removeAssignmentTarget: AssignmentRemovalTarget?,
    val showAssignDialog: Boolean,
)

/**
 * Single-line setters + raw dialog states behind the top-sections construction (same #462
 * hoist — the Screen passes these plus method refs while
 * [userManagementTopSectionsActions] owns the derivations and the multi-line
 * refresh/create/assign bodies; data class so LPL/TMF-free).
 */
internal data class UserManagementTopSectionsCallbacks(
    val onSearchChange: (String) -> Unit,
    val onShowCreateUser: (Boolean) -> Unit,
    val onLoadUsers: () -> Unit,
    val onLoadBranches: () -> Unit,
    val onBranchSelected: (String?) -> Unit,
    val onResetAdministration: () -> Unit,
    val onShowCreateBranch: (Boolean) -> Unit,
    val onAssignmentBranchChange: (BranchResponse?) -> Unit,
    val onShowAssignDialog: (Boolean) -> Unit,
    val selectedBranch: BranchResponse?,
    val assignmentResult: UiState<AssignmentResponse>,
    val removeAssignmentTarget: AssignmentRemovalTarget?,
    val showAssignDialog: Boolean,
)

/**
 * Callbacks + gates for the user-list region ([UserManagementUserList] in
 * UserManagementOverlays.kt — #462 LongMethod burn; the LazyColumn + status-when moves
 * there because UserManagementScreen.kt sits at the detekt file-function wall).
 * The slot-order swap lambda is owned by the host (the Screen passes the
 * `swapSlots` method ref); single-line setters + derivations ride here so the call site
 * stays lean (data class so LongParameterList/TooManyFunctions-free).
 */
internal data class UserManagementUserListActions(
    val selectedBranchName: String,
    val slotRows: List<UserSlotRow>,
    val mutationsDisabled: Boolean,
    val searchQuery: String,
    val expandedIds: Set<String>,
    val userRowActions: UserManagementUserRowActions,
    val actionErrors: Map<String, String>,
    val onSwapSlots: (String, String, String) -> Unit,
    val onEditSlotTarget: (SlotEditTarget) -> Unit,
    val onRetry: () -> Unit,
)

/**
 * Derived list/branch selections for [UserManagementScreen] (#462 LongMethod burn —
 * the Screen keeps one slim `rememberUserManagementDerived` call while this holder
 * carries the filtered rows + branch + slot-order derivations; data class so
 * LongParameterList/TooManyFunctions-free, lives here with the other UserManagement
 * holders — MatchingDeclaration precedent).
 */
internal data class UserManagementDerived(
    val filteredUsers: List<UserSummaryResponse>,
    val selectedBranch: BranchResponse?,
    val slotRows: List<UserSlotRow>,
    val selectedBranchName: String?,
)

/**
 * Callbacks + gates for the member half of [UserManagementDialogHosts] (#462 LongMethod burn —
 * new-file split; UserManagementScreen.kt/Header.kt/Dialogs.kt all sit at the detekt
 * file-function wall). The host collects the dialog flows itself (duplicate StateFlow
 * subscriptions are cheap — LoginNoticeEffect precedent) so only dialog targets + single-line
 * dismiss setters ride here.
 */
internal data class UserManagementMemberDialogsActions(
    val deactivateTarget: UserSummaryResponse?,
    val onDismissDeactivate: () -> Unit,
    val slotEditTarget: SlotEditTarget?,
    val onDismissSlotEdit: () -> Unit,
    val showCreateUserDialog: Boolean,
    val onCloseCreateUser: () -> Unit,
    val roleEditTarget: UserSummaryResponse?,
    val onDismissRoleEdit: () -> Unit,
)

/**
 * Callbacks + gates for the branch-admin half of [UserManagementDialogHosts] (same #462
 * new-file split). Flow states stay in the host (self-collected, see above); the assign-close
 * rides two single-line setters so the host owns the multi-line close body and the Screen call
 * site stays lean (call-site lambda bodies count toward the caller LongMethod).
 */
internal data class UserManagementBranchDialogsActions(
    val showCreateBranchDialog: Boolean,
    val onCloseCreateBranch: () -> Unit,
    val showAssignUserDialog: Boolean,
    val assignmentBranch: BranchResponse?,
    val onAssignDialog: (Boolean) -> Unit,
    val onAssignmentBranchChange: (BranchResponse?) -> Unit,
    val removeAssignmentTarget: AssignmentRemovalTarget?,
    val onClearRemoveTarget: () -> Unit,
)

internal val SlotEditTargetSaver =
    Saver<SlotEditTarget?, List<String>>(
        save = { target ->
            target?.let {
                listOf(
                    it.branchId,
                    it.branchName,
                    it.assignmentId,
                    it.displayName,
                    it.currentSlot.toString(),
                )
            } ?: emptyList()
        },
        restore = { values ->
            if (values.size != SLOT_EDIT_TARGET_VALUE_COUNT) {
                null
            } else {
                values[SLOT_EDIT_SLOT_INDEX].toShortOrNull()?.let { slot ->
                    SlotEditTarget(
                        branchId = values[SLOT_EDIT_BRANCH_ID_INDEX],
                        branchName = values[SLOT_EDIT_BRANCH_NAME_INDEX],
                        assignmentId = values[SLOT_EDIT_ASSIGNMENT_ID_INDEX],
                        displayName = values[SLOT_EDIT_DISPLAY_NAME_INDEX],
                        currentSlot = slot,
                    )
                }
            }
        },
    )

private const val SLOT_EDIT_TARGET_VALUE_COUNT = 5
private const val SLOT_EDIT_BRANCH_ID_INDEX = 0
private const val SLOT_EDIT_BRANCH_NAME_INDEX = 1
private const val SLOT_EDIT_ASSIGNMENT_ID_INDEX = 2
private const val SLOT_EDIT_DISPLAY_NAME_INDEX = 3
private const val SLOT_EDIT_SLOT_INDEX = 4

/** D3 — deactivate confirmation stating the consequences; reactivate stays a direct action. */
@Composable
internal fun DeactivateConfirmDialog(
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

/**
 * D4 — tap-to-edit slot number (mobile primary; manual number fallback on desktop). Client-side
 * validation mirrors the backend's 400 ("Slot must be 1 or greater") so an invalid input never
 * leaves the dialog.
 */
@Composable
internal fun EditSlotDialog(
    target: SlotEditTarget,
    options: SlotDialogOptions,
    onDismiss: () -> Unit,
    onSave: (Short) -> Unit,
) {
    var input by remember(target.assignmentId) { mutableStateOf(target.currentSlot.toString()) }
    var inputError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (options.dismissEnabled) onDismiss() },
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
                    enabled = !options.mutationsDisabled,
                )
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
                enabled = !options.mutationsDisabled,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = options.dismissEnabled) {
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
internal fun InviteMintDialog(
    mintState: UiState<InviteMintResponse>,
    onMint: (InviteMintRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val form = remember { InviteMintForm() }

    val inFlight = mintState is UiState.Loading
    val complete = listOf(form.username, form.email, form.displayName).all { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite user") },
        text = {
            Column {
                when (val state = mintState) {
                    is UiState.Success -> MintedCodePanel(state.data)
                    else -> InviteMintFields(form, inFlight, state as? UiState.Error)
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
                                    username = form.username.trim(),
                                    email = form.email.trim(),
                                    displayName = form.displayName.trim(),
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

private class InviteMintForm {
    var username by mutableStateOf("")
    var email by mutableStateOf("")
    var displayName by mutableStateOf("")
}

/** The Success panel: the single-use code is the only deliverable, so it renders until Done. */
@Composable
private fun MintedCodePanel(minted: InviteMintResponse) {
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(minted) {
        logInfo("UserManagementScreen", "mintState=Success; code ready to copy")
    }
    Text(
        text = "Share this single-use code. It expires ${minted.expiresAt}.",
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
            text = minted.inviteCode,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(Spacing.md),
        )
    }
    Spacer(Modifier.size(Spacing.sm))
    TextButton(onClick = {
        clipboard.setText(AnnotatedString(minted.inviteCode))
    }) {
        Text("Copy code")
    }
}

/** The entry fields plus the inline 400/409 error (the usersState=Error sticky-log shape). */
@Composable
private fun InviteMintFields(
    form: InviteMintForm,
    inFlight: Boolean,
    error: UiState.Error?,
) {
    if (error != null) {
        LaunchedEffect(error) {
            // Sticky branch — log once per state, not per recomposition.
            logWarn("UserManagementScreen", "mintState=Error: ${error.message}")
        }
    }
    OutlinedTextField(
        value = form.username,
        onValueChange = { form.username = it },
        label = { Text("Username") },
        singleLine = true,
        enabled = !inFlight,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(Spacing.sm))
    OutlinedTextField(
        value = form.email,
        onValueChange = { form.email = it },
        label = { Text("Email") },
        singleLine = true,
        enabled = !inFlight,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(Spacing.sm))
    OutlinedTextField(
        value = form.displayName,
        onValueChange = { form.displayName = it },
        label = { Text("Display name") },
        singleLine = true,
        enabled = !inFlight,
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let { errorState ->
        Text(
            text = errorState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/**
 * #345 — full-replace role editor backed by `GET /api/roles` (seeded bundles only — SUPERUSER
 * absent by design). Loading/error render in place with tap-to-retry (the BranchPicker shape);
 * Save issues the PUT only over a loaded bundle list. The checkbox set starts from the row's
 * current roles intersected with what the picker offers.
 */
internal data class RoleEditActions(
    val onRetryRoles: () -> Unit,
    val onDismiss: () -> Unit,
    val onSave: (List<String>) -> Unit,
)

@Composable
internal fun RoleEditDialog(
    user: UserSummaryResponse,
    rolesState: UiState<List<RoleResponse>>,
    mutationsDisabled: Boolean,
    actions: RoleEditActions,
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
        if (rolesState is UiState.Idle) actions.onRetryRoles()
    }

    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text("Edit roles — ${user.displayName}") },
        text = {
            RoleEditBody(
                state =
                    RoleEditBodyState(
                        rolesState = rolesState,
                        options = options,
                        mutationsDisabled = mutationsDisabled,
                        selected = selected,
                    ),
                onPick = { picked -> selected = picked },
                onRetryRoles = actions.onRetryRoles,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { actions.onSave(selected.toList()) },
                enabled = !mutationsDisabled && options.isNotEmpty(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private class RoleEditBodyState(
    val rolesState: UiState<List<RoleResponse>>,
    val options: List<RoleResponse>,
    val mutationsDisabled: Boolean,
    val selected: Set<String>,
)

@Composable
private fun RoleEditBody(
    state: RoleEditBodyState,
    onPick: (Set<String>) -> Unit,
    onRetryRoles: () -> Unit,
) {
    val rolesState = state.rolesState
    val rolesLoading = rolesState is UiState.Loading || rolesState is UiState.Idle
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

            state.options.isEmpty() -> {
                Text(
                    text = "No roles configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                RoleOptionList(state.options, state.selected, !state.mutationsDisabled, onPick)
            }
        }
    }
}

@Composable
private fun RoleOptionList(
    options: List<RoleResponse>,
    selected: Set<String>,
    enabled: Boolean,
    onToggle: (Set<String>) -> Unit,
) {
    options.forEach { role ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) {
                        onToggle(
                            if (role.name in selected) selected - role.name else selected + role.name,
                        )
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = role.name in selected,
                onCheckedChange = null,
                enabled = enabled,
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

/**
 * Shared slot-order card chrome (#135 D4): Surface + "Slot order — <branch>" header + empty
 * state + trailing inline errors. Platform actuals render their rows through [content] (desktop
 * swap arrows / android tap-to-edit — the #95 responsive split). Moved here from
 * UserManagementScreen.kt under the #462 file-function budget split.
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

/**
 * Clickable user-row header (identity + roles line + status badge), hoisted out of [UserRow]
 * under #462 and moved here under the file-function budget split. The badge `when` folds into
 * the Text value arg to keep the helper well under 60.
 */
@Composable
internal fun UserRowHeader(
    user: UserSummaryResponse,
    onToggleExpanded: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .rowHover(),
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
        Surface(
            shape = RoundedCornerShape(CornerRadius.sm),
            color = MaterialTheme.colorScheme.secondary,
        ) {
            Text(
                text =
                    when (user.status) {
                        UserStatus.ACTIVE -> "ACTIVE"
                        UserStatus.INACTIVE -> "INACTIVE"
                    },
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
}
