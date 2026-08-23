package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.ProfileViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * #381 — the signed-in user's own profile: identity, branch assignments (with Branch Slot),
 * a readable capability summary, and the self slot edit. Pushed route (the AuditLogHistory
 * shape — content-level Back TextButton), entry-scoped VM (#112). No admin affordances: the
 * only mutation is the backend's `updateSlot` self-leg on own assignments.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
) {
    val me by viewModel.me.collectAsState()
    val slotUpdate by viewModel.slotUpdate.collectAsState()
    var slotEditTarget by remember { mutableStateOf<SlotEditTarget?>(null) }

    LaunchedEffect(Unit) {
        // Load once per VM lifetime; re-fire wholesale from an error state (the
        // AuditLogHistory entry policy — retry re-fetches all three sections).
        if (me is UiState.Idle || me is UiState.Error) {
            viewModel.loadAll()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("← Back")
            }
            Text(
                text = "Profile",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }

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
                    onSlotEdit = { target -> slotEditTarget = target },
                )
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    slotEditTarget?.let { target ->
        EditSlotDialog(
            target = target,
            mutationsDisabled = slotUpdate is UiState.Loading,
            onDismiss = { slotEditTarget = null },
            onSave = { slot ->
                // Save closes immediately (the User Management precedent); the VM refreshes
                // the rows on success and a failure surfaces inline above the list.
                slotEditTarget = null
                viewModel.updateSlot(target.branchId, target.userId, slot)
            },
        )
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
        SectionDivider()

        Text("Branch assignments", style = MaterialTheme.typography.titleMedium)
        AssignmentsSectionBody(
            branchState = branches,
            slotUpdate = slotUpdate,
            user = user,
            onSlotEdit = onSlotEdit,
            onRetry = viewModel::loadBranches,
        )
        SectionDivider()

        Text("Capabilities", style = MaterialTheme.typography.titleMedium)
        CapabilitiesSectionBody(
            capState = capabilities,
            branchRows = (branches as? UiState.Success)?.data ?: emptyList(),
            onRetry = viewModel::loadAll,
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
            InlineSectionError(message = branchState.message, onRetry = onRetry)
        }

        is UiState.Success -> {
            val error = slotUpdate as? UiState.Error
            if (error != null) {
                InlineSectionError(message = error.message, onRetry = null)
            }
            // Relief rows carry no assignment (slot = null server-side), so mapNotNull on
            // the slot yields exactly the own assignments.
            val assignments =
                branchState.data.mapNotNull { row ->
                    row.slot?.let { Triple(row.branchId, row.branchName, it) }
                }
            if (assignments.isEmpty()) {
                EmptySectionText("No branch assignments yet — an admin assigns you from User Management.")
            } else {
                assignments.forEach { (branchId, branchName, slot) ->
                    AssignmentRow(
                        branchName = branchName,
                        slot = slot,
                        onEdit = {
                            onSlotEdit(
                                SlotEditTarget(
                                    branchId = branchId,
                                    branchName = branchName,
                                    userId = user.id,
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

@Composable
private fun CapabilitiesSectionBody(
    capState: UiState<List<UserCapabilityResponse>>,
    branchRows: List<MeBranchResponse>,
    onRetry: () -> Unit,
) {
    when (capState) {
        is UiState.Error -> {
            InlineSectionError(message = capState.message, onRetry = onRetry)
        }

        is UiState.Success -> {
            if (capState.data.isEmpty()) {
                EmptySectionText("No capabilities — you gain access once assigned to a branch.")
            } else {
                val branchNames = branchRows.associate { it.branchId to it.branchName }
                capState.data.forEach { cap ->
                    CapabilityRow(
                        code = cap.capabilityCode,
                        scope = scopeLabel(cap.contextType, cap.contextId, branchNames),
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

@Composable
private fun CapabilityRow(
    code: String,
    scope: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
    ) {
        Text(code, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(scope, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
    }
}

/** Inline section error; [onRetry] null for action failures (no reload fixes them). */
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

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(Spacing.md))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Spacer(Modifier.height(Spacing.sm))
}

/**
 * Readable capability scope: GLOBAL → "Global"; BRANCH resolves the name from the loaded
 * assignment rows (falls back to the raw context id); every other context reads as its
 * grant kind (day grants, mission/tour delegations).
 */
private fun scopeLabel(
    contextType: CapabilityContextType,
    contextId: String,
    branchNames: Map<String, String>,
): String =
    when (contextType) {
        CapabilityContextType.GLOBAL -> "Global"
        CapabilityContextType.BRANCH -> branchNames[contextId] ?: "Branch $contextId"
        CapabilityContextType.BRANCH_DAY -> "Day grant"
        CapabilityContextType.MEDICAL_MISSION -> "Mission delegation"
        CapabilityContextType.PROVINCIAL_TOUR -> "Tour delegation"
    }
