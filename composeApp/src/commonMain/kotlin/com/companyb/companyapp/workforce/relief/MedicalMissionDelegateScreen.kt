@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.companyb.companyapp.workforce.relief

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.identity.UserSummaryResponse
import com.companyb.companyapp.contracts.workforce.AssignDelegateRequest
import com.companyb.companyapp.contracts.workforce.DelegateResponse
import com.companyb.companyapp.ui.EmptyState
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.workforce.relief.DelegateViewModel
import com.companyb.companyapp.workforce.team.UserViewModel
import kotlin.uuid.Uuid

private const val MANAGER_ROLE = "MANAGER"

fun medicalMissionBranches(branches: List<BranchResponse>): List<BranchResponse> =
    branches.filter { it.branchType == BranchType.MEDICAL_MISSION }

fun eligibleDelegateUsers(
    users: List<UserSummaryResponse>,
    delegates: List<DelegateResponse>,
): List<UserSummaryResponse> {
    val activeDelegateIds = delegates.filter { it.endedAt == null }.mapTo(mutableSetOf()) { it.targetUser }
    return users
        .filter {
            it.status == UserStatus.ACTIVE &&
                MANAGER_ROLE in it.roles &&
                it.id !in activeDelegateIds
        }.sortedBy { it.displayName }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // #563 workforce relief owner, grandfather retarget
fun MedicalMissionDelegateScreen(
    delegateViewModel: DelegateViewModel,
    userViewModel: UserViewModel,
    modifier: Modifier = Modifier,
) {
    val branchesState by userViewModel.branches.collectAsState()
    val usersState by userViewModel.users.collectAsState()
    val heldUsers by userViewModel.freshestUsers.collectAsState()
    val delegatesState by delegateViewModel.delegates.collectAsState()
    val assignState by delegateViewModel.assignResult.collectAsState()
    val revokeState by delegateViewModel.revokeResult.collectAsState()

    var selectedBranchId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var assignmentRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var revokeTarget by remember { mutableStateOf<DelegateResponse?>(null) }
    var lastLoadedBranches by remember { mutableStateOf(emptyList<BranchResponse>()) }

    LaunchedEffect(Unit) {
        logInfo("MedicalMissionDelegateScreen", "composable entered (first composition)")
        userViewModel.loadBranches()
        userViewModel.loadUsers()
    }

    val loadedBranches = (branchesState as? UiState.Success<List<BranchResponse>>)?.data
    LaunchedEffect(loadedBranches) {
        if (loadedBranches != null) lastLoadedBranches = loadedBranches
    }
    val availableBranches = loadedBranches ?: lastLoadedBranches
    val missionBranches = remember(availableBranches) { medicalMissionBranches(availableBranches) }
    LaunchedEffect(missionBranches, selectedBranchId) {
        if (selectedBranchId !in missionBranches.map { it.id }) {
            selectedBranchId = missionBranches.firstOrNull()?.id
        }
    }

    val selectedBranch = missionBranches.firstOrNull { it.id == selectedBranchId }
    LaunchedEffect(selectedBranch?.id) {
        selectedTargetId = null
        assignmentRequestId = null
        revokeTarget = null
        delegateViewModel.clearMutationResults()
        if (selectedBranch == null) {
            delegateViewModel.clearDelegates()
        } else {
            delegateViewModel.loadDelegates(selectedBranch.id)
        }
    }

    LaunchedEffect(assignState) {
        when (val state = assignState) {
            is UiState.Success -> {
                logInfo("MedicalMissionDelegateScreen", "assignDelegate succeeded")
                val assigned = state.data
                if (assigned.branchId == selectedBranchId) {
                    selectedTargetId = null
                    assignmentRequestId = null
                    delegateViewModel.loadDelegates(assigned.branchId, force = true)
                }
            }

            is UiState.Error -> {
                logWarn("MedicalMissionDelegateScreen", "assignDelegate failed: ${state.message}")
            }

            else -> {
                Unit
            }
        }
    }

    LaunchedEffect(revokeState) {
        when (val state = revokeState) {
            is UiState.Success -> {
                logInfo("MedicalMissionDelegateScreen", "revokeDelegate succeeded")
                revokeTarget = null
                selectedBranchId?.let { delegateViewModel.loadDelegates(it, force = true) }
            }

            is UiState.Error -> {
                logWarn("MedicalMissionDelegateScreen", "revokeDelegate failed: ${state.message}")
            }

            else -> {
                Unit
            }
        }
    }

    LaunchedEffect(branchesState) {
        if (branchesState is UiState.Error) {
            // SAFETY: `is` check above; delegated State value doesn't smart-cast #467
            logWarn("MedicalMissionDelegateScreen", "branchesState failed: ${(branchesState as UiState.Error).message}")
        }
    }
    LaunchedEffect(usersState) {
        if (usersState is UiState.Error) {
            // SAFETY: `is` check above; delegated State value doesn't smart-cast #467
            logWarn("MedicalMissionDelegateScreen", "usersState failed: ${(usersState as UiState.Error).message}")
        }
    }
    LaunchedEffect(delegatesState) {
        if (delegatesState is UiState.Error) {
            logWarn(
                "MedicalMissionDelegateScreen",
                // SAFETY: `is` check above; delegated State value doesn't smart-cast #467
                "delegatesState failed: ${(delegatesState as UiState.Error).message}",
            )
        }
    }

    val delegates = (delegatesState as? UiState.Success<List<DelegateResponse>>)?.data.orEmpty()
    val users = heldUsers.orEmpty()
    val eligibleUsers = eligibleDelegateUsers(users, delegates)
    val userNames = users.associate { it.id to it.displayName }
    val mutationsDisabled =
        assignState is UiState.Loading ||
            revokeState is UiState.Loading ||
            delegatesState !is UiState.Success
    val assignmentDisabled = mutationsDisabled || usersState !is UiState.Success
    val refreshDisabled =
        assignState is UiState.Loading ||
            revokeState is UiState.Loading ||
            branchesState is UiState.Loading ||
            usersState is UiState.Loading ||
            delegatesState is UiState.Loading

    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Medical mission delegates", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Assign active Manager users to manage mission attendance",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
            TextButton(
                onClick = {
                    delegateViewModel.clearMutationResults()
                    userViewModel.loadBranches()
                    userViewModel.loadUsers()
                    selectedBranch?.let { delegateViewModel.loadDelegates(it.id, force = true) }
                },
                enabled = !refreshDisabled,
            ) {
                Text("Refresh")
            }
        }

        when {
            branchesState is UiState.Error -> {
                // SAFETY: `is` check in this `when` branch; delegated State value doesn't smart-cast #467
                val error = branchesState as UiState.Error
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    ErrorCard(error.message, userViewModel::loadBranches)
                }
            }

            branchesState !is UiState.Success -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            missionBranches.isEmpty() -> {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    EmptyState("No medical mission branches")
                }
            }

            else -> {
                MissionBranchPicker(
                    branches = missionBranches,
                    selectedBranchId = selectedBranchId,
                    enabled = !refreshDisabled,
                    onBranchSelected = {
                        selectedBranchId = it
                        revokeTarget = null
                    },
                )
                if (selectedBranch != null) {
                    DelegateAssignmentForm(
                        usersState = usersState,
                        eligibleUsers = eligibleUsers,
                        selectedTargetId = selectedTargetId,
                        mutationsDisabled = assignmentDisabled,
                        assignState = assignState,
                        onTargetSelected = {
                            selectedTargetId = it
                            assignmentRequestId = it?.let { Uuid.random().toString() }
                        },
                        onAssign = {
                            val targetId = selectedTargetId
                            if (targetId != null) {
                                delegateViewModel.assignDelegate(
                                    AssignDelegateRequest(
                                        delegateId =
                                            assignmentRequestId ?: Uuid.random().toString().also {
                                                assignmentRequestId = it
                                            },
                                        targetUserId = targetId,
                                        branchId = selectedBranch.id,
                                    ),
                                )
                            }
                        },
                    )
                    DelegateList(
                        state = delegatesState,
                        userNames = userNames,
                        mutationsDisabled = mutationsDisabled,
                        onRetry = { delegateViewModel.loadDelegates(selectedBranch.id, force = true) },
                        onRevoke = { revokeTarget = it },
                    )
                }
            }
        }

        if (revokeState is UiState.Error) {
            Text(
                // SAFETY: `is` check above; delegated State value doesn't smart-cast #467
                text = (revokeState as UiState.Error).message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                if (revokeState !is UiState.Loading) revokeTarget = null
            },
            title = { Text("Revoke delegate?") },
            text = {
                Text("Remove ${userNames[target.targetUser] ?: target.targetUser} from this medical mission?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        delegateViewModel.revokeDelegate(target.id)
                    },
                    enabled = !mutationsDisabled,
                ) {
                    Text(if (revokeState is UiState.Loading) "Revoking..." else "Revoke")
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }, enabled = revokeState !is UiState.Loading) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "LongParameterList") // #563 workforce relief owner, grandfather retarget
private fun DelegateAssignmentForm(
    usersState: UiState<List<UserSummaryResponse>>,
    eligibleUsers: List<UserSummaryResponse>,
    selectedTargetId: String?,
    mutationsDisabled: Boolean,
    assignState: UiState<DelegateResponse>,
    onTargetSelected: (String?) -> Unit,
    onAssign: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = eligibleUsers.firstOrNull { it.id == selectedTargetId }
    Surface(
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Assign a Manager", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!mutationsDisabled && eligibleUsers.isNotEmpty()) expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = selected?.displayName ?: "No eligible Manager selected",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Eligible Manager") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    enabled = !mutationsDisabled && eligibleUsers.isNotEmpty(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    eligibleUsers.forEach { user ->
                        DropdownMenuItem(
                            text = { Text("${user.displayName} (${user.username})") },
                            onClick = {
                                onTargetSelected(user.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
            if (usersState is UiState.Error) {
                Text(
                    text = "Eligible users unavailable: ${usersState.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (usersState !is UiState.Success && eligibleUsers.isEmpty()) {
                Text("Loading eligible users…", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            } else if (eligibleUsers.isEmpty()) {
                Text("No active Manager users available", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            }
            TextButton(
                onClick = onAssign,
                enabled = selected != null && !mutationsDisabled,
            ) {
                Text(if (assignState is UiState.Loading) "Assigning..." else "Assign delegate")
            }
            if (assignState is UiState.Error) {
                Text(
                    text = assignState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissionBranchPicker(
    branches: List<BranchResponse>,
    selectedBranchId: String?,
    enabled: Boolean,
    onBranchSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = branches.firstOrNull { it.id == selectedBranchId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.let { "${it.name} (MEDICAL_MISSION)" } ?: "Select a medical mission",
            onValueChange = {},
            readOnly = true,
            label = { Text("Medical mission branch") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            enabled = enabled,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch.name) },
                    onClick = {
                        if (enabled) {
                            onBranchSelected(branch.id)
                            expanded = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.DelegateList(
    state: UiState<List<DelegateResponse>>,
    userNames: Map<String, String>,
    mutationsDisabled: Boolean,
    onRetry: () -> Unit,
    onRevoke: (DelegateResponse) -> Unit,
) {
    when (state) {
        is UiState.Error -> {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                ErrorCard(state.message, onRetry)
            }
        }

        is UiState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Success -> {
            if (state.data.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    EmptyState("No delegate assignments")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(state.data, key = { it.id }) { delegate ->
                        DelegateRow(
                            delegate = delegate,
                            displayName = userNames[delegate.targetUser] ?: delegate.targetUser,
                            enabled = !mutationsDisabled,
                            onRevoke = { onRevoke(delegate) },
                        )
                    }
                }
            }
        }

        UiState.Idle -> {
            Unit
        }
    }
}

@Composable
private fun DelegateRow(
    delegate: DelegateResponse,
    displayName: String,
    enabled: Boolean,
    onRevoke: () -> Unit,
) {
    val active = delegate.endedAt == null
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(CornerRadius.sm),
        modifier = Modifier.fillMaxWidth().alpha(if (active) 1f else DELEGATE_REVOKED_ALPHA),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().rowHover().padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (active) "Active delegate" else "Revoked",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
            if (active) {
                TextButton(onClick = onRevoke, enabled = enabled) { Text("Revoke") }
            }
        }
    }
}

private const val DELEGATE_REVOKED_ALPHA = 0.65f
