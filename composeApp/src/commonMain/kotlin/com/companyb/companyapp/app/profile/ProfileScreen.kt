package com.companyb.companyapp.app.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.app.ProfileViewModel
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.branch.MeBranchResponse
import com.companyb.companyapp.contracts.identity.MeResponse
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.workforce.team.EditSlotDialog
import com.companyb.companyapp.workforce.team.SlotDialogOptions
import com.companyb.companyapp.workforce.team.SlotEditTarget
import com.companyb.companyapp.workforce.team.SlotEditTargetSaver

/**
 * #381 — the signed-in user's own profile: identity heading, branch assignments (with
 * Branch Slot), a compact Access summary, and the self slot edit. Entry-scoped VM
 * (#112). #682 — shell-owned destination (no local Back: the shell titles "Profile");
 * the raw capability dump is replaced by navigation-derived destinations plus a
 * collapsed technical disclosure. No admin affordances: the only mutation is the
 * backend's `updateSlot` self-leg on own assignments.
 */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val me by viewModel.me.collectAsState()
    val slotUpdate by viewModel.slotUpdate.collectAsState()
    var slotEditTarget by
        rememberSaveable(stateSaver = SlotEditTargetSaver) {
            mutableStateOf<SlotEditTarget?>(null)
        }
    LaunchedEffect(slotUpdate) {
        if (slotUpdate is UiState.Success) {
            slotEditTarget = null
        }
    }

    // Load once per VM lifetime; re-fire wholesale from an error state (the
    // AuditLogHistory entry policy — retry re-fetches all three sections).
    LaunchedEffect(Unit) { if (me is UiState.Idle || me is UiState.Error) viewModel.loadAll() }

    ProfileScreenBody(
        me = me,
        viewModel = viewModel,
        slotUpdate = slotUpdate,
        onSlotEdit = { target ->
            viewModel.resetSlotUpdate()
            slotEditTarget = target
        },
    )

    slotEditTarget?.let { target ->
        EditSlotDialog(
            target = target,
            onDismiss = { slotEditTarget = null },
            onSave = { slot ->
                viewModel.updateSlot(target.branchId, target.assignmentId, slot)
            },
            options =
                SlotDialogOptions(
                    mutationsDisabled = slotUpdate is UiState.Loading,
                    errorMessage = (slotUpdate as? UiState.Error)?.message,
                ),
        )
    }
}

@Composable
private fun ProfileScreenBody(
    me: UiState<MeResponse>,
    viewModel: ProfileViewModel,
    slotUpdate: UiState<Unit>,
    onSlotEdit: (SlotEditTarget) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        when (val meState = me) {
            is UiState.Error -> {
                logWarn("ProfileScreen", "meState=Error: ${meState.message}")
                ErrorCard(message = meState.message, onRetry = viewModel::loadAll)
            }

            is UiState.Success -> {
                ProfileContent(
                    user = meState.data,
                    viewModel = viewModel,
                    slotUpdate = slotUpdate,
                    onSlotEdit = onSlotEdit,
                )
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    user: MeResponse,
    viewModel: ProfileViewModel,
    slotUpdate: UiState<Unit>,
    onSlotEdit: (SlotEditTarget) -> Unit,
) {
    val branches by viewModel.branches.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val retainedCapabilities by viewModel.freshestCapabilities.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = user.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "@${user.username} · ${user.status.name.lowercase()}",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
        )
        Spacer(Modifier.height(Spacing.md))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(Spacing.sm))

        Text("Branch assignments", style = MaterialTheme.typography.titleMedium)
        AssignmentsSectionBody(
            branchState = branches,
            slotUpdate = slotUpdate,
            user = user,
            onSlotEdit = onSlotEdit,
            onRetry = {
                viewModel.resetSlotUpdate()
                viewModel.loadBranches()
            },
        )
        Spacer(Modifier.height(Spacing.md))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(Spacing.sm))

        AccessSectionBody(
            capState = capabilities,
            retainedCaps = retainedCapabilities,
            onRefresh = viewModel::loadCapabilities,
            onRetry = viewModel::loadCapabilities,
        )
    }
}

@Composable
private fun AssignmentsSectionBody(
    branchState: UiState<List<MeBranchResponse>>,
    slotUpdate: UiState<Unit>,
    user: MeResponse,
    onSlotEdit: (SlotEditTarget) -> Unit,
    onRetry: () -> Unit,
) {
    when (branchState) {
        is UiState.Error -> {
            logWarn("ProfileScreen", "branches=Error: ${branchState.message}")
            InlineSectionError(message = branchState.message, onRetry = onRetry)
        }

        is UiState.Success -> {
            val error = slotUpdate as? UiState.Error
            if (error != null) {
                InlineSectionError(message = error.message, onRetry = onRetry)
            }
            // Relief rows carry no assignment (assignmentId and slot are null server-side).
            val assignments = branchState.data.filter { it.assignmentId != null && it.slot != null }
            if (assignments.isEmpty()) {
                EmptySectionText(
                    "No branch assignments yet. " +
                        "Ask an Owner, Manager or Coordinator with team-management access " +
                        "to check your assignment.",
                )
            } else {
                assignments.forEach { row ->
                    val assignmentId = row.assignmentId ?: return@forEach
                    val slot = row.slot ?: return@forEach
                    // #682 — the unresolved-name fallback rides into the edit target too,
                    // so the dialog names the same branch the row shows.
                    val branchName = row.branchName.ifBlank { "Branch name unavailable" }
                    AssignmentRow(
                        branchName = branchName,
                        slot = slot,
                        onEdit = {
                            onSlotEdit(
                                SlotEditTarget(
                                    branchId = row.branchId,
                                    branchName = branchName,
                                    assignmentId = assignmentId,
                                    displayName = user.displayName,
                                    currentSlot = slot,
                                ),
                            )
                        },
                    )
                }
            }
        }

        else -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * #682 — the compact Access section: read-only destination summary derived from
 * navigation's capability predicates, with independent refresh/retry (branch and
 * capability reads fail independently). A pending refresh keeps known destinations
 * visible with a status; a failed load reads "Access unavailable" (never "No access").
 * Raw capability rows live only inside the collapsed technical disclosure.
 */
@Composable
private fun AccessSectionBody(
    capState: UiState<List<UserCapabilityResponse>>,
    retainedCaps: List<UserCapabilityResponse>?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    var technicalExpanded by rememberSaveable { mutableStateOf(false) }

    Column {
        AccessSectionHeader(isBusy = capState is UiState.Loading, onRefresh = onRefresh)
        // #682 — the known list survives Loading/Error frames (VM freshest); only a first
        // load with nothing retained falls through to the spinner/error below.
        val visibleCaps = (capState as? UiState.Success)?.data ?: retainedCaps
        if (visibleCaps != null) {
            if (capState is UiState.Loading) {
                Text(
                    text = "Refreshing access…",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            DestinationGroups(caps = visibleCaps)
            if (capState is UiState.Error) {
                logWarn("ProfileScreen", "capabilities=Error: ${capState.message}")
                InlineSectionError(message = "Access unavailable", onRetry = onRetry)
            }
            TechnicalDetails(
                caps = visibleCaps,
                expanded = technicalExpanded,
                onToggle = { technicalExpanded = !technicalExpanded },
            )
        } else {
            when (capState) {
                is UiState.Error -> {
                    logWarn("ProfileScreen", "capabilities=Error: ${capState.message}")
                    InlineSectionError(message = "Access unavailable", onRetry = onRetry)
                }

                else -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessSectionHeader(
    isBusy: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Access",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRefresh, enabled = !isBusy) {
            Text("Refresh access")
        }
    }
    Text(
        text = "Access is determined by your roles and branch or day grants.",
        style = MaterialTheme.typography.bodySmall,
        color = InkSubtle,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

/** #682 — grouped destination names; empty grants keep the always-available rows plus the no-access note. */
@Composable
private fun DestinationGroups(caps: List<UserCapabilityResponse>) {
    val groups = accessDestinationGroups(caps)
    DestinationGroup(heading = "Always available", names = groups.always)
    DestinationGroup(heading = "Branch access", names = groups.branch)
    DestinationGroup(heading = "Global access", names = groups.global)
    if (groups.branch.isEmpty() && groups.global.isEmpty()) {
        Text(
            text =
                "No work access is available. " +
                    "Ask your team administrator to check your role and branch access.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    } else {
        Text(
            text = "Destinations reflect navigation access, not every action within them.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun DestinationGroup(
    heading: String,
    names: List<String>,
) {
    if (names.isEmpty()) return
    Text(
        text = heading,
        style = MaterialTheme.typography.labelLarge,
        color = InkSubtle,
        modifier = Modifier.padding(top = Spacing.sm),
    )
    names.forEach { name ->
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/**
 * #682 — collapsed technical inventory for support: exact code, scope kind and
 * identifier per row, selectable, no decorative badges. Capabilities may change after
 * clock-in or grant changes; there is no preference editor here.
 */
@Composable
private fun TechnicalDetails(
    caps: List<UserCapabilityResponse>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    TextButton(onClick = onToggle) {
        Text(if (expanded) "Hide technical access details" else "Technical access details")
    }
    if (expanded) {
        Text(
            text = "Capabilities may change after clock-in or grant changes.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
        SelectionContainer {
            Column {
                caps.forEach { cap ->
                    Column(Modifier.padding(top = Spacing.xs)) {
                        Text(cap.capabilityCode, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${cap.contextType} · ${cap.contextId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSubtle,
                        )
                    }
                }
            }
        }
    }
}

/** Own-assignment row: branch name + current slot + the self-service edit affordance. */
@Composable
private fun AssignmentRow(
    branchName: String,
    slot: Short,
    onEdit: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(branchName, style = MaterialTheme.typography.bodyLarge)
            Text("Slot $slot", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        }
        TextButton(onClick = onEdit) {
            Text("Edit slot")
        }
    }
}

/** Inline section error; retry reloads owning read so stale assignment targets can be refreshed. */
@Composable
private fun InlineSectionError(
    message: String,
    onRetry: (() -> Unit)?,
) {
    Column(Modifier.padding(top = Spacing.sm)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptySectionText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = InkSubtle,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}
