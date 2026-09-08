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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.identity.UserSummaryResponse
import com.companyb.companyapp.contracts.workforce.AssignDelegateRequest
import com.companyb.companyapp.contracts.workforce.DelegateResponse
import com.companyb.companyapp.ui.EmptyState
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.contract.ColdLoadPlaceholder
import com.companyb.companyapp.ui.contract.CurrentObjectHeading
import com.companyb.companyapp.ui.contract.DestructiveConfirmDialog
import com.companyb.companyapp.ui.contract.InlineStatus
import com.companyb.companyapp.ui.contract.InlineStatusKind
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.SecondaryLabel
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.contract.operationalField
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
// #598 screen orchestrator stays whole (state derivation + layout; #535 no arbitrary split).
@Suppress("LongMethod", "CyclomaticComplexMethod") // #598
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
    // #670 — dialog close returns focus: Cancel paths restore to the intact invoker via the
    // platform; a landed revoke removes the row's Revoke button, so focus moves to Refresh
    // (nearest surviving control) only on that transition — never on Cancel.
    val headerFocus = remember { FocusRequester() }
    var refocusAfterRevoke by remember { mutableStateOf(false) }

    val loadedBranches = (branchesState as? UiState.Success<List<BranchResponse>>)?.data
    val availableBranches = loadedBranches ?: lastLoadedBranches
    val missionBranches = remember(availableBranches) { medicalMissionBranches(availableBranches) }
    val selectedBranch = missionBranches.firstOrNull { it.id == selectedBranchId }

    DelegateLoadEffects(
        userViewModel = userViewModel,
        delegateViewModel = delegateViewModel,
        loadedBranches = loadedBranches,
        missionBranches = missionBranches,
        selectedBranchId = selectedBranchId,
        selectedBranch = selectedBranch,
        onBranchesLoaded = { lastLoadedBranches = it },
        onAutoSelectBranch = { selectedBranchId = it },
        onBranchChanged = {
            selectedTargetId = null
            assignmentRequestId = null
            revokeTarget = null
        },
    )
    DelegateOutcomeEffects(
        delegateViewModel = delegateViewModel,
        assignState = assignState,
        revokeState = revokeState,
        selectedBranchId = selectedBranchId,
        onAssignLanded = {
            selectedTargetId = null
            assignmentRequestId = null
        },
        onRevokeLanded = {
            revokeTarget = null
            refocusAfterRevoke = true
        },
    )
    DelegateErrorEffects(
        branchesState = branchesState,
        usersState = usersState,
        delegatesState = delegatesState,
    )

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
        DelegateScreenHeader(
            refreshDisabled = refreshDisabled,
            refreshFocus = headerFocus,
            onRefresh = {
                delegateViewModel.clearMutationResults()
                userViewModel.loadBranches()
                userViewModel.loadUsers()
                selectedBranch?.let { delegateViewModel.loadDelegates(it.id, force = true) }
            },
        )
        DelegateScreenBody(
            branchesState = branchesState,
            missionBranches = missionBranches,
            selectedBranchId = selectedBranchId,
            selectedBranch = selectedBranch,
            usersState = usersState,
            eligibleUsers = eligibleUsers,
            selectedTargetId = selectedTargetId,
            assignmentDisabled = assignmentDisabled,
            assignState = assignState,
            delegatesState = delegatesState,
            userNames = userNames,
            mutationsDisabled = mutationsDisabled,
            refreshDisabled = refreshDisabled,
            onBranchSelected = {
                selectedBranchId = it
                revokeTarget = null
            },
            onTargetSelected = {
                selectedTargetId = it
                assignmentRequestId = it?.let { Uuid.random().toString() }
            },
            onAssign = {
                val targetId = selectedTargetId
                if (targetId != null && selectedBranch != null) {
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
            onRetryBranches = userViewModel::loadBranches,
            onRetryDelegates = { selectedBranch?.let { delegateViewModel.loadDelegates(it.id, force = true) } },
            onRevoke = { revokeTarget = it },
        )
        if (revokeState is UiState.Error) {
            InlineStatus(
                // SAFETY: `is` check above; delegated State value doesn't smart-cast #467
                message = (revokeState as UiState.Error).message,
                kind = InlineStatusKind.FAILURE,
            )
        }
    }

    DelegateRevokeDialog(
        revokeTarget = revokeTarget,
        userNames = userNames,
        mutationsDisabled = mutationsDisabled,
        revokeState = revokeState,
        onDismiss = { revokeTarget = null },
        onConfirm = { delegateViewModel.revokeDelegate(it) },
    )
    LaunchedEffect(revokeTarget, refocusAfterRevoke) {
        if (revokeTarget == null && refocusAfterRevoke) {
            refocusAfterRevoke = false
            runCatching { headerFocus.requestFocus() }
        }
    }
}

@Composable
// #598 9-param effects stay whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #598
private fun DelegateLoadEffects(
    userViewModel: UserViewModel,
    delegateViewModel: DelegateViewModel,
    loadedBranches: List<BranchResponse>?,
    missionBranches: List<BranchResponse>,
    selectedBranchId: String?,
    selectedBranch: BranchResponse?,
    onBranchesLoaded: (List<BranchResponse>) -> Unit,
    onAutoSelectBranch: (String?) -> Unit,
    onBranchChanged: () -> Unit,
) {
    LaunchedEffect(Unit) {
        logInfo("MedicalMissionDelegateScreen", "composable entered (first composition)")
        userViewModel.loadBranches()
        userViewModel.loadUsers()
    }
    LaunchedEffect(loadedBranches) {
        if (loadedBranches != null) onBranchesLoaded(loadedBranches)
    }
    LaunchedEffect(missionBranches, selectedBranchId) {
        if (selectedBranchId !in missionBranches.map { it.id }) {
            onAutoSelectBranch(missionBranches.firstOrNull()?.id)
        }
    }
    LaunchedEffect(selectedBranch?.id) {
        onBranchChanged()
        delegateViewModel.clearMutationResults()
        if (selectedBranch == null) {
            delegateViewModel.clearDelegates()
        } else {
            delegateViewModel.loadDelegates(selectedBranch.id)
        }
    }
}

@Composable
// #598 6-param effects stay whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #598
private fun DelegateOutcomeEffects(
    delegateViewModel: DelegateViewModel,
    assignState: UiState<DelegateResponse>,
    revokeState: UiState<Unit>,
    selectedBranchId: String?,
    onAssignLanded: () -> Unit,
    onRevokeLanded: () -> Unit,
) {
    LaunchedEffect(assignState) {
        when (val state = assignState) {
            is UiState.Success -> {
                logInfo("MedicalMissionDelegateScreen", "assignDelegate succeeded")
                val assigned = state.data
                if (assigned.branchId == selectedBranchId) {
                    onAssignLanded()
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
                onRevokeLanded()
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
}

@Composable
private fun DelegateErrorEffects(
    branchesState: UiState<List<BranchResponse>>,
    usersState: UiState<List<UserSummaryResponse>>,
    delegatesState: UiState<List<DelegateResponse>>,
) {
    LaunchedEffect(branchesState) {
        if (branchesState is UiState.Error) {
            logWarn("MedicalMissionDelegateScreen", "branchesState failed: ${branchesState.message}")
        }
    }
    LaunchedEffect(usersState) {
        if (usersState is UiState.Error) {
            logWarn("MedicalMissionDelegateScreen", "usersState failed: ${usersState.message}")
        }
    }
    LaunchedEffect(delegatesState) {
        if (delegatesState is UiState.Error) {
            logWarn(
                "MedicalMissionDelegateScreen",
                "delegatesState failed: ${delegatesState.message}",
            )
        }
    }
}

@Composable
private fun DelegateScreenHeader(
    refreshDisabled: Boolean,
    onRefresh: () -> Unit,
    refreshFocus: FocusRequester? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // #670 — current object owns the 24sp slot; description recedes.
            CurrentObjectHeading(text = "Medical mission delegates")
            SecondaryLabel(text = "Assign active Manager users to manage mission attendance")
        }
        TertiaryActionButton(
            label = "Refresh",
            onClick = onRefresh,
            enabled = !refreshDisabled,
            modifier = if (refreshFocus != null) Modifier.focusRequester(refreshFocus) else Modifier,
        )
    }
}

@Composable
// #598 19-param body stays whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #598
private fun ColumnScope.DelegateScreenBody(
    branchesState: UiState<List<BranchResponse>>,
    missionBranches: List<BranchResponse>,
    selectedBranchId: String?,
    selectedBranch: BranchResponse?,
    usersState: UiState<List<UserSummaryResponse>>,
    eligibleUsers: List<UserSummaryResponse>,
    selectedTargetId: String?,
    assignmentDisabled: Boolean,
    assignState: UiState<DelegateResponse>,
    delegatesState: UiState<List<DelegateResponse>>,
    userNames: Map<String, String>,
    mutationsDisabled: Boolean,
    refreshDisabled: Boolean,
    onBranchSelected: (String) -> Unit,
    onTargetSelected: (String?) -> Unit,
    onAssign: () -> Unit,
    onRetryBranches: () -> Unit,
    onRetryDelegates: () -> Unit,
    onRevoke: (DelegateResponse) -> Unit,
) {
    // #670 — a refresh (explicit Refresh button) retains the populated region: stale rows
    // stay mounted under an UPDATING/FAILURE banner instead of unmounting to a spinner.
    // Cold loads (nothing retained) keep the bounded placeholder / error card.
    val hasRetained = missionBranches.isNotEmpty()
    when {
        branchesState is UiState.Error && !hasRetained -> {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                ErrorCard(branchesState.message, onRetryBranches)
            }
        }

        branchesState !is UiState.Success && !hasRetained -> {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                ColdLoadPlaceholder(message = "Loading branches…")
            }
        }

        missionBranches.isEmpty() -> {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                EmptyState("No medical mission branches")
            }
        }

        else -> {
            if (branchesState is UiState.Loading) {
                InlineStatus(message = "Refreshing branches…", kind = InlineStatusKind.UPDATING)
            } else if (branchesState is UiState.Error) {
                InlineStatus(
                    message = branchesState.message,
                    kind = InlineStatusKind.FAILURE,
                    onRetry = onRetryBranches,
                )
            }
            DelegateBranchContent(
                missionBranches = missionBranches,
                selectedBranchId = selectedBranchId,
                selectedBranch = selectedBranch,
                usersState = usersState,
                eligibleUsers = eligibleUsers,
                selectedTargetId = selectedTargetId,
                assignmentDisabled = assignmentDisabled,
                assignState = assignState,
                delegatesState = delegatesState,
                userNames = userNames,
                mutationsDisabled = mutationsDisabled,
                refreshDisabled = refreshDisabled,
                onBranchSelected = onBranchSelected,
                onTargetSelected = onTargetSelected,
                onAssign = onAssign,
                onRetryDelegates = onRetryDelegates,
                onRevoke = onRevoke,
            )
        }
    }
}

@Composable
// #598 17-param content stays whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #598
private fun ColumnScope.DelegateBranchContent(
    missionBranches: List<BranchResponse>,
    selectedBranchId: String?,
    selectedBranch: BranchResponse?,
    usersState: UiState<List<UserSummaryResponse>>,
    eligibleUsers: List<UserSummaryResponse>,
    selectedTargetId: String?,
    assignmentDisabled: Boolean,
    assignState: UiState<DelegateResponse>,
    delegatesState: UiState<List<DelegateResponse>>,
    userNames: Map<String, String>,
    mutationsDisabled: Boolean,
    refreshDisabled: Boolean,
    onBranchSelected: (String) -> Unit,
    onTargetSelected: (String?) -> Unit,
    onAssign: () -> Unit,
    onRetryDelegates: () -> Unit,
    onRevoke: (DelegateResponse) -> Unit,
) {
    MissionBranchPicker(
        branches = missionBranches,
        selectedBranchId = selectedBranchId,
        enabled = !refreshDisabled,
        onBranchSelected = onBranchSelected,
    )
    if (selectedBranch != null) {
        DelegateAssignmentForm(
            usersState = usersState,
            eligibleUsers = eligibleUsers,
            selectedTargetId = selectedTargetId,
            mutationsDisabled = assignmentDisabled,
            assignState = assignState,
            onTargetSelected = onTargetSelected,
            onAssign = onAssign,
        )
        DelegateList(
            state = delegatesState,
            userNames = userNames,
            mutationsDisabled = mutationsDisabled,
            onRetry = onRetryDelegates,
            onRevoke = onRevoke,
        )
    }
}

@Composable
// #598 6-param dialog stays whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #598
private fun DelegateRevokeDialog(
    revokeTarget: DelegateResponse?,
    userNames: Map<String, String>,
    mutationsDisabled: Boolean,
    revokeState: UiState<Unit>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    revokeTarget?.let { target ->
        // #670 — destructive confirm: safe-action initial focus, pinned while busy.
        DestructiveConfirmDialog(
            title = "Revoke delegate?",
            body = "Remove ${userNames[target.targetUser] ?: target.targetUser} from this medical mission?",
            confirmLabel = "Revoke",
            onConfirm = { onConfirm(target.id) },
            onDismiss = onDismiss,
            isBusy = revokeState is UiState.Loading,
            confirmEnabled = !mutationsDisabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// #598 7-param entry stays whole (declarative-UI signature; #535 no arbitrary DTO).
@Suppress("LongParameterList") // #598
private fun DelegateAssignmentForm(
    usersState: UiState<List<UserSummaryResponse>>,
    eligibleUsers: List<UserSummaryResponse>,
    selectedTargetId: String?,
    mutationsDisabled: Boolean,
    assignState: UiState<DelegateResponse>,
    onTargetSelected: (String?) -> Unit,
    onAssign: () -> Unit,
) {
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
            EligibleManagerDropdown(
                eligibleUsers = eligibleUsers,
                selected = selected,
                mutationsDisabled = mutationsDisabled,
                onTargetSelected = onTargetSelected,
            )
            AssignmentEligibilityStatus(
                usersState = usersState,
                eligibleUsers = eligibleUsers,
            )
            AssignmentActionRow(
                selected = selected,
                mutationsDisabled = mutationsDisabled,
                assignState = assignState,
                onAssign = onAssign,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EligibleManagerDropdown(
    eligibleUsers: List<UserSummaryResponse>,
    selected: UserSummaryResponse?,
    mutationsDisabled: Boolean,
    onTargetSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!mutationsDisabled && eligibleUsers.isNotEmpty()) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.displayName ?: "No eligible Manager selected",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Eligible Manager") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).operationalField(),
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
}

@Composable
private fun AssignmentEligibilityStatus(
    usersState: UiState<List<UserSummaryResponse>>,
    eligibleUsers: List<UserSummaryResponse>,
) {
    if (usersState is UiState.Error) {
        InlineStatus(
            message = "Eligible users unavailable: ${usersState.message}",
            kind = InlineStatusKind.FAILURE,
        )
    } else if (usersState !is UiState.Success && eligibleUsers.isEmpty()) {
        InlineStatus(message = "Loading eligible users…", kind = InlineStatusKind.UPDATING)
    } else if (eligibleUsers.isEmpty()) {
        SecondaryLabel(text = "No active Manager users available")
    }
}

@Composable
private fun AssignmentActionRow(
    selected: UserSummaryResponse?,
    mutationsDisabled: Boolean,
    assignState: UiState<DelegateResponse>,
    onAssign: () -> Unit,
) {
    // #670 — one filled primary per task region; label + bounds persist while assigning.
    PrimaryActionButton(
        label = "Assign delegate",
        onClick = onAssign,
        enabled = selected != null && !mutationsDisabled,
        isBusy = assignState is UiState.Loading,
    )
    if (assignState is UiState.Error) {
        InlineStatus(message = assignState.message, kind = InlineStatusKind.FAILURE)
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
            singleLine = true,
            label = { Text("Medical mission branch") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).operationalField(),
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
                // #670 — shared inline failure + Retry (never steals focus); the revoke
                // confirm stays a dialog, this is the list leg.
                InlineStatus(message = state.message, kind = InlineStatusKind.FAILURE, onRetry = onRetry)
            }
        }

        is UiState.Loading -> {
            // #670 — bounded placeholder; branch-switch loads must not retain the prior
            // branch's rows (cross-branch bleed class), so no keep-last here.
            Box(Modifier.fillMaxWidth().weight(1f)) {
                ColdLoadPlaceholder(message = "Loading delegates…")
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
                TertiaryActionButton(label = "Revoke", onClick = onRevoke, enabled = enabled)
            }
        }
    }
}

private const val DELEGATE_REVOKED_ALPHA = 0.65f
