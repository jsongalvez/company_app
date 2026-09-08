package com.companyb.companyapp.workforce.branch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branch.BranchClockInStatus
import com.companyb.companyapp.contracts.branch.MeBranchResponse
import com.companyb.companyapp.contracts.workforce.ActiveShiftResponse
import com.companyb.companyapp.contracts.workforce.ClockInResponse
import com.companyb.companyapp.ui.contract.ColdLoadPlaceholder
import com.companyb.companyapp.ui.contract.InlineStatus
import com.companyb.companyapp.ui.contract.InlineStatusKind
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.workforce.branch.BranchSelectViewModel
import com.companyb.companyapp.workforce.relief.ReliefAccessViewModel
import com.companyb.companyapp.workforce.relief.ReliefInvitePlanningContent
import com.companyb.companyapp.workforce.relief.ReliefInviteViewModel
import com.companyb.companyapp.workforce.relief.ReliefRequestPanel

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
    reliefInviteViewModel: ReliefInviteViewModel,
    reliefAccessViewModel: ReliefAccessViewModel,
    onClockInComplete: () -> Unit,
) {
    val branchesState by viewModel.branches.collectAsState()
    val clockInState by viewModel.clockInState.collectAsState()
    val refreshState by viewModel.refreshState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()

    // #160 — the inviter side (placement per #106): an inline "Invite staff" panel per branch
    // card (candidate search + date pick + sent-invites list). Toggling is local composition
    // state; the branch gate (active assignment) is backend-authoritative, so the panel is
    // offered on every card and a 403 surfaces inline.
    // #160 — the inviter side (placement per #106): an inline "Invite staff" panel per branch
    // card (candidate search + date pick + sent-invites list). Toggling is local composition
    // state; the branch gate (active assignment) is backend-authoritative, so the panel is
    // offered on every card and a 403 surfaces inline.
    var inviteBranchId by remember { mutableStateOf<String?>(null) }
    // #670 — populated branch rows stay mounted across reloads (restore-null restale);
    // Loading with retained rows renders them under an UPDATING banner instead of swapping.
    var lastBranches by remember { mutableStateOf(emptyList<MeBranchResponse>()) }
    LaunchedEffect(branchesState) {
        (branchesState as? UiState.Success<List<MeBranchResponse>>)?.let { lastBranches = it.data }
    }

    BranchSelectStatusEffects(
        clockInState = clockInState,
        refreshState = refreshState,
        restoreState = restoreState,
        onClockInComplete = onClockInComplete,
        branchesState = branchesState,
        onLoadBranches = { viewModel.loadBranches() },
    )

    // NOTE (#462 LPL burn): Success-branch derivations (busy gate, error lines) live in
    // BranchSuccessHost, which self-collects clockIn/refresh — the Screen keeps one slim call.

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        BranchSelectHeader()

        // #357 — the outsider's pre-clock-in relief request (collapsed by default so the
        // clock-in flow stays primary).
        ReliefRequestPanel(viewModel = reliefAccessViewModel)

        Spacer(modifier = Modifier.height(Spacing.sm))

        when (val state = branchesState) {
            is UiState.Loading -> {
                if (lastBranches.isEmpty()) {
                    // #670 — cold load uses a bounded placeholder, not a full-screen takeover.
                    ColdLoadPlaceholder(message = "Loading branches…")
                } else {
                    InlineStatus(message = "Refreshing branches…", kind = InlineStatusKind.UPDATING)
                    BranchSuccessContent(
                        branches = lastBranches,
                        inviteBranchId = inviteBranchId,
                        onToggleInvite = { inviteBranchId = it },
                        viewModel = viewModel,
                        reliefInviteViewModel = reliefInviteViewModel,
                    )
                }
            }

            is UiState.Error -> {
                if (lastBranches.isEmpty()) {
                    BranchLoadErrorContent(
                        message = state.message,
                        onRetry = { viewModel.loadBranches() },
                    )
                } else {
                    InlineStatus(
                        message = state.message,
                        kind = InlineStatusKind.FAILURE,
                        onRetry = { viewModel.loadBranches() },
                    )
                    BranchSuccessContent(
                        branches = lastBranches,
                        inviteBranchId = inviteBranchId,
                        onToggleInvite = { inviteBranchId = it },
                        viewModel = viewModel,
                        reliefInviteViewModel = reliefInviteViewModel,
                    )
                }
            }

            is UiState.Success -> {
                BranchSuccessContent(
                    branches = state.data,
                    inviteBranchId = inviteBranchId,
                    onToggleInvite = { inviteBranchId = it },
                    viewModel = viewModel,
                    reliefInviteViewModel = reliefInviteViewModel,
                )
            }

            is UiState.Idle -> {}
        }
    }
}

@Composable
private fun BranchSuccessContent(
    branches: List<MeBranchResponse>,
    inviteBranchId: String?,
    onToggleInvite: (String?) -> Unit,
    viewModel: BranchSelectViewModel,
    reliefInviteViewModel: ReliefInviteViewModel,
) {
    // Self-collected (duplicate StateFlow subscriptions cheap — LoginNoticeEffect precedent):
    // keeps this signature at 5 params and the Screen call site to one slim call.
    val clockInState by viewModel.clockInState.collectAsState()
    val refreshState by viewModel.refreshState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val isPhase3Busy =
        clockInState is UiState.Loading || refreshState is UiState.Loading || restoreState is UiState.Loading
    // A failed refresh means the clock-in itself succeeded — the only legal retry is the
    // refresh (re-clock-in would hit ShiftGuard's single-active-clock-in 409).
    val refreshError = (refreshState as? UiState.Error)?.message
    val clockInError = (clockInState as? UiState.Error)?.message
    // #669 — a failed resume retries the read-only resolve (never a clock-in POST).
    val restoreError = (restoreState as? UiState.Error)?.message
    val canClockIn = refreshError == null && !isPhase3Busy
    val canContinue = refreshError == null && !isPhase3Busy
    if (branches.isEmpty()) {
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
        BranchErrorBanners(
            clockInError = clockInError,
            refreshError = refreshError,
            restoreError = restoreError,
            onRetryRefresh = { viewModel.refreshCapabilities() },
            onRetryRestore = { viewModel.restoreShift() },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(branches, key = { it.branchId }) { branch ->
                BranchCard(
                    branch = branch,
                    isClockingIn = isPhase3Busy,
                    canClockIn = canClockIn,
                    canContinue = canContinue,
                    inviteExpanded = inviteBranchId == branch.branchId,
                    actions =
                        BranchCardActions(
                            onClockIn = { viewModel.clockIn(branch) },
                            onContinue = { viewModel.restoreShift() },
                            onToggleInvite = {
                                val id = branch.branchId
                                onToggleInvite(if (inviteBranchId == id) null else id)
                            },
                        ),
                )
                if (inviteBranchId == branch.branchId) {
                    InviteStaffPanel(
                        branchId = branch.branchId,
                        branchName = branch.branchName,
                        viewModel = reliefInviteViewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchSelectStatusEffects(
    clockInState: UiState<ClockInResponse>,
    refreshState: UiState<Unit>,
    restoreState: UiState<ActiveShiftResponse?>,
    onClockInComplete: () -> Unit,
    branchesState: UiState<List<MeBranchResponse>>,
    onLoadBranches: () -> Unit,
) {
    LaunchedEffect(Unit) {
        logInfo("BranchSelectScreen", "composable entered (first composition)")
        if (branchesState is UiState.Idle) {
            onLoadBranches()
        }
    }

    LaunchedEffect(clockInState) {
        when (clockInState) {
            is UiState.Error -> {
                logWarn("BranchSelectScreen", "clockInState=Error: ${clockInState.message}")
            }

            else -> {}
        }
    }

    LaunchedEffect(refreshState) {
        when (refreshState) {
            is UiState.Success -> {
                logInfo("BranchSelectScreen", "refreshState=Success, navigating to Dashboard")
                onClockInComplete()
            }

            is UiState.Error -> {
                logWarn("BranchSelectScreen", "refreshState=Error: ${refreshState.message}")
            }

            else -> {}
        }
    }

    LaunchedEffect(restoreState) {
        // #669 — server-reported absence (closed between reads): the HERE row is stale —
        // reload so it falls back to Not clocked in. A restored shift navigates via the
        // chained refresh-success effect above, never from here.
        when (val state = restoreState) {
            is UiState.Success -> {
                if (state.data == null) {
                    logInfo("BranchSelectScreen", "restoreState=Success(null), reloading branches")
                    onLoadBranches()
                }
            }

            is UiState.Error -> {
                logWarn("BranchSelectScreen", "restoreState=Error: ${state.message}")
            }

            else -> {}
        }
    }
}

/** LPL-free carrier for [BranchCard] (#462 burn — 6 params → 5). */
private data class BranchCardActions(
    val onClockIn: () -> Unit,
    val onContinue: () -> Unit,
    val onToggleInvite: () -> Unit,
)

@Composable
private fun BranchCard(
    branch: MeBranchResponse,
    isClockingIn: Boolean,
    canClockIn: Boolean,
    canContinue: Boolean,
    inviteExpanded: Boolean,
    actions: BranchCardActions,
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
            BranchCardInfo(
                branch = branch,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(Spacing.sm))

            if (branch.clockInStatus == BranchClockInStatus.NOT_CLOCKED_IN) {
                BranchClockInButton(
                    isClockingIn = isClockingIn,
                    canClockIn = canClockIn,
                    onClockIn = actions.onClockIn,
                )
            } else if (showContinueFor(branch.clockInStatus)) {
                // #669 — an already-clocked row resumes through the launch/login
                // resolver (read-only); no dead-end label and no second clock-in.
                BranchContinueButton(
                    branchName = branch.branchName,
                    busy = isClockingIn,
                    enabled = canContinue,
                    onContinue = actions.onContinue,
                )
            }
        }
        // #160 — inviter affordance, independent of clock-in (anyone assigned can invite;
        // the panel gate is backend-authoritative). Sits on the card's footer so the
        // invite entry point is one tap from every branch.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.xs),
            horizontalArrangement = Arrangement.End,
        ) {
            TertiaryActionButton(
                label = if (inviteExpanded) "Close" else "Invite staff",
                onClick = actions.onToggleInvite,
            )
        }
    }
}

/**
 * #160 — the BranchSelect inviter side (placement per #106): clock-in stays primary on the
 * card; this panel is the secondary Relief access entry for pre-shift needs.
 * #680 — the panel renders the shared relief-planning owner ([ReliefInvitePlanningContent])
 * with explicit branch/date — the same implementation the Sessions Team Relief tab uses —
 * so managing invitations never forces a clock-out. No viewed date pre-shift, so the
 * default resolves to tomorrow.
 */
@Composable
private fun InviteStaffPanel(
    branchId: String,
    branchName: String?,
    viewModel: ReliefInviteViewModel,
) {
    ReliefInvitePlanningContent(
        branchId = branchId,
        branchName = branchName,
        viewedDate = null,
        inviteViewModel = viewModel,
    )
}
