@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)

package com.companyb.companyapp.workforce.branch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.branch.CreateBranchRequest
import com.companyb.companyapp.contracts.identity.UserAssignmentResponse
import com.companyb.companyapp.contracts.identity.UserSummaryResponse
import com.companyb.companyapp.contracts.workforce.AssignmentResponse
import com.companyb.companyapp.contracts.workforce.CreateAssignmentRequest
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.workforce.team.parseSlotInput
import com.companyb.companyapp.workforce.team.slotInputError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal fun branchNameError(
    name: String,
    branchType: BranchType = BranchType.CLINIC,
    existingBranches: List<BranchResponse> = emptyList(),
): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isBlank() -> {
            "Branch name is required"
        }

        existingBranches.any { it.branchType == branchType && it.name == trimmed } -> {
            "A branch with this name and type already exists"
        }

        else -> {
            null
        }
    }
}

internal data class AssignmentRemovalTarget(
    val userId: String,
    val displayName: String,
    val assignment: UserAssignmentResponse,
)

internal data class AssignmentDialogActions(
    val onAssign: (CreateAssignmentRequest) -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun CreateBranchDialog(
    state: UiState<BranchResponse>,
    existingBranches: List<BranchResponse>,
    mutationsDisabled: Boolean,
    onCreate: (CreateBranchRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val form = remember { CreateBranchForm() }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val loading = state is UiState.Loading
    val disabled = mutationsDisabled
    val nameError = branchNameError(form.name, form.branchType, existingBranches).takeIf { attempted }

    AlertDialog(
        onDismissRequest = { if (!disabled) onDismiss() },
        title = { Text("Create branch") },
        text = {
            CreateBranchDialogContent(
                content =
                    CreateBranchContentState(
                        form = form,
                        typeMenuOpen = typeMenuOpen,
                        disabled = disabled,
                        nameError = nameError,
                        state = state,
                    ),
                onTypeMenuOpenChange = { typeMenuOpen = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attempted = true
                    if (branchNameError(form.name, form.branchType, existingBranches) == null) {
                        onCreate(
                            CreateBranchRequest(
                                id = form.id,
                                name = form.name.trim(),
                                branchType = form.branchType,
                            ),
                        )
                    }
                },
                enabled = !disabled,
            ) {
                Text(if (loading) "Creating…" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !disabled) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun AssignUserDialog(
    branch: BranchResponse,
    users: List<UserSummaryResponse>,
    state: UiState<AssignmentResponse>,
    mutationsDisabled: Boolean,
    actions: AssignmentDialogActions,
) {
    val form = remember(branch.id) { AssignmentForm() }
    var userMenuOpen by remember { mutableStateOf(false) }
    val loading = state is UiState.Loading
    val disabled = mutationsDisabled
    val selectedUser = users.firstOrNull { it.id == form.userId }
    val userError = if (form.attempted && selectedUser == null) "Select a user" else null
    val slotError = slotInputError(form.slot).takeIf { form.attempted }

    AlertDialog(
        onDismissRequest = { if (!disabled) actions.onDismiss() },
        title = { Text("Assign user — ${branch.name}") },
        text = {
            AssignUserDialogContent(
                content =
                    AssignUserContentState(
                        branch = branch,
                        users = users,
                        form = form,
                        userMenuOpen = userMenuOpen,
                        selectedUser = selectedUser,
                        userError = userError,
                        slotError = slotError,
                        disabled = disabled,
                        state = state,
                    ),
                onUserMenuOpenChange = { userMenuOpen = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    form.attempted = true
                    val parsedSlot = parseSlotInput(form.slot)
                    if (selectedUser != null && parsedSlot != null) {
                        actions.onAssign(
                            CreateAssignmentRequest(
                                id = form.id,
                                userId = selectedUser.id,
                                slot = parsedSlot,
                            ),
                        )
                    }
                },
                enabled = !disabled && users.isNotEmpty(),
            ) {
                Text(if (loading) "Assigning…" else "Assign")
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismiss, enabled = !disabled) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun RemoveAssignmentDialog(
    target: AssignmentRemovalTarget,
    state: UiState<Unit>,
    mutationsDisabled: Boolean,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val loading = state is UiState.Loading
    val disabled = mutationsDisabled
    AlertDialog(
        onDismissRequest = { if (!disabled) onDismiss() },
        title = { Text("Remove assignment?") },
        text = {
            Column {
                Text(
                    text =
                        "Remove ${target.displayName} from ${target.assignment.branchName}? " +
                            "Their historical records stay intact, but this home-branch access ends.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                (state as? UiState.Error)?.let { error ->
                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRemove, enabled = !disabled) {
                Text(if (loading) "Removing…" else "Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !disabled) {
                Text("Cancel")
            }
        },
    )
}

private class CreateBranchForm {
    val id = Uuid.random().toString()
    var name by mutableStateOf("")
    var branchType by mutableStateOf(BranchType.CLINIC)
}

private class AssignmentForm {
    val id = Uuid.random().toString()
    var userId by mutableStateOf<String?>(null)
    var slot by mutableStateOf("1")
    var attempted by mutableStateOf(false)
}

private data class CreateBranchContentState(
    val form: CreateBranchForm,
    val typeMenuOpen: Boolean,
    val disabled: Boolean,
    val nameError: String?,
    val state: UiState<BranchResponse>,
)

private data class AssignUserContentState(
    val branch: BranchResponse,
    val users: List<UserSummaryResponse>,
    val form: AssignmentForm,
    val userMenuOpen: Boolean,
    val selectedUser: UserSummaryResponse?,
    val userError: String?,
    val slotError: String?,
    val disabled: Boolean,
    val state: UiState<AssignmentResponse>,
)

private data class UserAssignmentPickerState(
    val branch: BranchResponse,
    val users: List<UserSummaryResponse>,
    val selectedUser: UserSummaryResponse?,
    val expanded: Boolean,
    val enabled: Boolean,
    val userError: String?,
)

private fun branchTypeLabel(type: BranchType): String =
    when (type) {
        BranchType.CLINIC -> "Clinic"

        BranchType.PROVINCIAL_TOUR -> "Provincial tour"

        BranchType.MEDICAL_MISSION -> "Medical mission"

        // #876 — forward-compat sentinel: a newer server branch type degrades the label,
        // never the row.
        BranchType.UNKNOWN -> "Unknown"
    }

private fun assignmentUserLabel(
    user: UserSummaryResponse,
    branchId: String,
): String {
    val role = user.roles.joinToString().ifBlank { "No role" }
    val assignmentState =
        if (user.assignments.any { it.branchId == branchId }) "already assigned" else "not assigned"
    return "${user.displayName} · $role · $assignmentState"
}

@Composable
private fun CreateBranchDialogContent(
    content: CreateBranchContentState,
    onTypeMenuOpenChange: (Boolean) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = content.form.name,
            onValueChange = { content.form.name = it },
            label = { Text("Branch name") },
            singleLine = true,
            enabled = !content.disabled,
            isError = content.nameError != null,
            supportingText = { content.nameError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(Spacing.sm))
        BranchTypePicker(
            selectedType = content.form.branchType,
            expanded = content.typeMenuOpen,
            enabled = !content.disabled,
            onExpandedChange = onTypeMenuOpenChange,
            onTypeSelected = { content.form.branchType = it },
        )
        (content.state as? UiState.Error)?.let { error ->
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun AssignUserDialogContent(
    content: AssignUserContentState,
    onUserMenuOpenChange: (Boolean) -> Unit,
) {
    Column {
        if (content.users.isEmpty()) {
            Text(
                text = "No users available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            UserAssignmentPicker(
                state =
                    UserAssignmentPickerState(
                        branch = content.branch,
                        users = content.users,
                        selectedUser = content.selectedUser,
                        expanded = content.userMenuOpen,
                        enabled = !content.disabled,
                        userError = content.userError,
                    ),
                onExpandedChange = onUserMenuOpenChange,
                onUserSelected = {
                    content.form.userId = it.id
                    content.form.attempted = false
                },
            )
            content.selectedUser?.let { user ->
                Text(
                    text = "Role: ${user.roles.joinToString().ifBlank { "No role" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        Spacer(Modifier.size(Spacing.sm))
        OutlinedTextField(
            value = content.form.slot,
            onValueChange = { content.form.slot = it },
            label = { Text("Slot number") },
            singleLine = true,
            enabled = !content.disabled,
            isError = content.slotError != null,
            supportingText = { content.slotError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        (content.state as? UiState.Error)?.let { error ->
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun BranchTypePicker(
    selectedType: BranchType,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTypeSelected: (BranchType) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) onExpandedChange(!expanded) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = branchTypeLabel(selectedType),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Branch type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            BranchType.entries.filter { it != BranchType.UNKNOWN }.forEach { type ->
                DropdownMenuItem(
                    text = { Text(branchTypeLabel(type)) },
                    onClick = {
                        onTypeSelected(type)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun UserAssignmentPicker(
    state: UserAssignmentPickerState,
    onExpandedChange: (Boolean) -> Unit,
    onUserSelected: (UserSummaryResponse) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = state.expanded,
        onExpandedChange = { if (state.enabled) onExpandedChange(!state.expanded) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = state.selectedUser?.let { assignmentUserLabel(it, state.branch.id) } ?: "Select a user",
            onValueChange = {},
            readOnly = true,
            enabled = state.enabled,
            label = { Text("User") },
            isError = state.userError != null,
            supportingText = { state.userError?.let { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = state.expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = state.expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            state.users.forEach { user ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = assignmentUserLabel(user, state.branch.id),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onUserSelected(user)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}
