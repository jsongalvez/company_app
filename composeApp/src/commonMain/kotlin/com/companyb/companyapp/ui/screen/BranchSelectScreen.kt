package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.domain.BranchClockInStatus
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.BranchSelectViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * #94-grad — BranchSelect surface (Phase 3 of the #94 outline): the caller's branches with
 * per-branch clock-in status from GET /api/me/branches (#98), and the clock-in action.
 *
 * - Status labels per spec line 185: "Clocked in here" / "Clocked in elsewhere" /
 *   "Not clocked in" / "Relief duty" (clocked-in-here as relief — `isRelief` = not assigned
 *   to this branch). NOT_CLOCKED_IN renders the muted label alongside the Clock In button.
 * - The clock-in button only renders for NOT_CLOCKED_IN branches: ShiftGuard enforces a single
 *   active clock-in, and clock-out is #97-grad fog — a HERE/ELSEWHERE branch has no legal
 *   action here.
 * - Phase-3 loading: the tapped branch's button stays busy while clock-in AND the ADR-0021
 *   capability refresh are both in flight; navigation to Dashboard fires only on refresh
 *   success (onClockInComplete), retry re-runs whichever step failed.
 */
@Composable
fun BranchSelectScreen(
    viewModel: BranchSelectViewModel,
    onClockInComplete: () -> Unit,
) {
    val branchesState by viewModel.branches.collectAsState()
    val clockInState by viewModel.clockInState.collectAsState()
    val refreshState by viewModel.refreshState.collectAsState()

    LaunchedEffect(Unit) {
        logInfo("BranchSelectScreen", "composable entered (first composition)")
        if (branchesState is UiState.Idle) {
            viewModel.loadBranches()
        }
    }

    LaunchedEffect(clockInState) {
        when (val state = clockInState) {
            is UiState.Error -> {
                logWarn("BranchSelectScreen", "clockInState=Error: ${state.message}")
            }

            else -> {}
        }
    }

    LaunchedEffect(refreshState) {
        when (val state = refreshState) {
            is UiState.Success -> {
                logInfo("BranchSelectScreen", "refreshState=Success, navigating to Dashboard")
                onClockInComplete()
            }

            is UiState.Error -> {
                logWarn("BranchSelectScreen", "refreshState=Error: ${state.message}")
            }

            else -> {}
        }
    }

    val isPhase3Busy = clockInState is UiState.Loading || refreshState is UiState.Loading
    // A failed refresh means the clock-in itself succeeded — the only legal retry is the
    // refresh (re-clock-in would hit ShiftGuard's single-active-clock-in 409).
    val refreshError = (refreshState as? UiState.Error)?.message
    val clockInError = (clockInState as? UiState.Error)?.message
    val canClockIn = refreshError == null && !isPhase3Busy

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        Text(
            text = "Select branch",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = "Clock in to start your day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        when (val state = branchesState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                logWarn("BranchSelectScreen", "branchesState=Error: ${state.message}")
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        OutlinedButton(onClick = { viewModel.loadBranches() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No branches assigned to you yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    clockInError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }
                    refreshError?.let { error ->
                        Text(
                            text = "$error — you're already clocked in.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        OutlinedButton(onClick = { viewModel.refreshCapabilities() }) {
                            Text("Retry")
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(state.data, key = { it.branchId }) { branch ->
                            BranchCard(
                                branch = branch,
                                isClockingIn = isPhase3Busy,
                                canClockIn = canClockIn,
                                onClockIn = { viewModel.clockIn(branch) },
                            )
                        }
                    }
                }
            }

            is UiState.Idle -> {}
        }
    }
}

@Composable
private fun BranchCard(
    branch: MeBranchResponse,
    isClockingIn: Boolean,
    canClockIn: Boolean,
    onClockIn: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (branch.clockInStatus == BranchClockInStatus.CLOCKED_IN_HERE) {
                        // secondary = Surface3 — the clocked-in-here highlight. NOT
                        // secondaryContainer: LinearDarkColors leaves it unmapped, which
                        // falls back to Material3's default purple (the #140 round-3 catch;
                        // the legacy HomeScreen carried the same bug).
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = branch.branchName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                BranchTypeBadge(branch.branchType)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = statusLabel(branch),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (branch.clockInStatus == BranchClockInStatus.NOT_CLOCKED_IN) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            if (branch.clockInStatus == BranchClockInStatus.NOT_CLOCKED_IN) {
                Button(
                    onClick = onClockIn,
                    enabled = canClockIn,
                ) {
                    if (isClockingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Clock In")
                    }
                }
            }
        }
    }
}

/**
 * Status label per spec line 185: "Clocked in here" / "Clocked in elsewhere" /
 * "Not clocked in" / "Relief duty". Relief duty replaces the here-label when the clock-in
 * was as relief (isRelief — not assigned to this branch, #98). NOT_CLOCKED_IN carries the
 * muted label alongside the Clock In button (the button is the action; the label is the
 * state per spec).
 */
private fun statusLabel(branch: MeBranchResponse): String =
    when (branch.clockInStatus) {
        BranchClockInStatus.CLOCKED_IN_HERE -> {
            if (branch.isRelief) {
                "Relief duty"
            } else {
                "Clocked in here"
            }
        }

        BranchClockInStatus.CLOCKED_IN_ELSEWHERE -> {
            "Clocked in elsewhere"
        }

        BranchClockInStatus.NOT_CLOCKED_IN -> {
            "Not clocked in"
        }
    }

@Composable
private fun BranchTypeBadge(branchType: BranchType) {
    val label =
        when (branchType) {
            BranchType.CLINIC -> "Clinic"
            BranchType.PROVINCIAL_TOUR -> "Provincial Tour"
            BranchType.MEDICAL_MISSION -> "Medical Mission"
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
