@file:Suppress("DEPRECATION")

package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.InviteMintRequest
import com.companyb.companyapp.dto.InviteMintResponse
import com.companyb.companyapp.dto.RoleResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.parseSlotInput
import com.companyb.companyapp.viewmodel.slotInputError

/**
 * Dialogs of the User Management screen (#345/#350), split out of UserManagementScreen.kt to
 * keep both files under the detekt file-function budget. Same package, internal visibility.
 *
 * The [LocalClipboardManager] copy affordance rides the deprecated-but-common clipboard API
 * until the project adopts the suspend [androidx.compose.ui.platform.LocalClipboard] migration
 * wholesale (same debt as UserManagementScreen.kt's file suppress).
 */
internal data class SlotEditTarget(
    val branchId: String,
    val branchName: String,
    val userId: String,
    val displayName: String,
    val currentSlot: Short,
)

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
