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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branch.BranchClockInStatus
import com.companyb.companyapp.contracts.branch.MeBranchResponse
import com.companyb.companyapp.contracts.workforce.ClockInResponse
import com.companyb.companyapp.contracts.workforce.ReliefInviteResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.workforce.branch.BranchSelectViewModel
import com.companyb.companyapp.workforce.relief.ReliefAccessViewModel
import com.companyb.companyapp.workforce.relief.ReliefInviteViewModel
import com.companyb.companyapp.workforce.relief.ReliefRequestPanel
import com.companyb.companyapp.workforce.relief.currentOperationalDate
import com.companyb.companyapp.workforce.relief.isInviteActionable
import com.companyb.companyapp.workforce.relief.isInviteExpired
import com.companyb.companyapp.workforce.relief.parseInviteDate
import kotlinx.datetime.LocalDate

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

    // #160 — the inviter side (placement per #106): an inline "Invite staff" panel per branch
    // card (candidate search + date pick + sent-invites list). Toggling is local composition
    // state; the branch gate (active assignment) is backend-authoritative, so the panel is
    // offered on every card and a 403 surfaces inline.
    // #160 — the inviter side (placement per #106): an inline "Invite staff" panel per branch
    // card (candidate search + date pick + sent-invites list). Toggling is local composition
    // state; the branch gate (active assignment) is backend-authoritative, so the panel is
    // offered on every card and a 403 surfaces inline.
    var inviteBranchId by remember { mutableStateOf<String?>(null) }

    BranchSelectStatusEffects(
        clockInState = clockInState,
        refreshState = refreshState,
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                BranchLoadErrorContent(
                    message = state.message,
                    onRetry = { viewModel.loadBranches() },
                )
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
    val isPhase3Busy = clockInState is UiState.Loading || refreshState is UiState.Loading
    // A failed refresh means the clock-in itself succeeded — the only legal retry is the
    // refresh (re-clock-in would hit ShiftGuard's single-active-clock-in 409).
    val refreshError = (refreshState as? UiState.Error)?.message
    val clockInError = (clockInState as? UiState.Error)?.message
    val canClockIn = refreshError == null && !isPhase3Busy
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
            onRetryRefresh = { viewModel.refreshCapabilities() },
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
                    inviteExpanded = inviteBranchId == branch.branchId,
                    actions =
                        BranchCardActions(
                            onClockIn = { viewModel.clockIn(branch) },
                            onToggleInvite = {
                                val id = branch.branchId
                                onToggleInvite(if (inviteBranchId == id) null else id)
                            },
                        ),
                )
                if (inviteBranchId == branch.branchId) {
                    InviteStaffPanel(
                        branchId = branch.branchId,
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
}

/** LPL-free carrier for [BranchCard] (#462 burn — 6 params → 5). */
private data class BranchCardActions(
    val onClockIn: () -> Unit,
    val onToggleInvite: () -> Unit,
)

@Composable
private fun BranchCard(
    branch: MeBranchResponse,
    isClockingIn: Boolean,
    canClockIn: Boolean,
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
            TextButton(onClick = actions.onToggleInvite) {
                Text(if (inviteExpanded) "Close" else "Invite staff")
            }
        }
    }
}

/**
 * #160 — the BranchSelect inviter side (placement per #106): date pick (defaults to
 * tomorrow — the invite use case is future-day planning), candidate search (username /
 * displayName prefix; results tap-to-invite), and the sent-invites list with Retract on
 * PENDING rows (the Q6 lifecycle — a sent list without retract would render the endpoint
 * dead). The panel is branch-scoped; every request carries the branchId path param.
 */
@Composable
private fun InviteStaffPanel(
    branchId: String,
    viewModel: ReliefInviteViewModel,
) {
    var dateText by remember { mutableStateOf(defaultInviteDate()) }
    var query by remember { mutableStateOf("") }

    val validDate = parseInviteDate(dateText)

    // NOTE (#462 LPL burn): result/error/queue states are self-collected inside each child
    // (duplicate StateFlow subscriptions cheap — LoginNoticeEffect precedent), so the panel
    // keeps one slim call per child.
    InvitePanelEffects(
        viewModel = viewModel,
        branchId = branchId,
        query = query,
        dateText = dateText,
        validDate = validDate,
    )

    InvitePanelForm(
        viewModel = viewModel,
        state = InvitePanelFormState(dateText = dateText, query = query, validDate = validDate),
        callbacks = InvitePanelFormCallbacks(onDateChange = { dateText = it }, onQueryChange = { query = it }),
    ) {
        InvitePanelResults(
            branchId = branchId,
            viewModel = viewModel,
            dateText = dateText,
            validDate = validDate,
        )
    }
}

/** LPL/TMF-free carriers for [InvitePanelForm] (#462 burn — 9 params → viewModel + state + callbacks). */
private data class InvitePanelFormState(
    val dateText: String,
    val query: String,
    val validDate: LocalDate?,
)

private data class InvitePanelFormCallbacks(
    val onDateChange: (String) -> Unit,
    val onQueryChange: (String) -> Unit,
)

@Composable
private fun InvitePanelEffects(
    viewModel: ReliefInviteViewModel,
    branchId: String,
    query: String,
    dateText: String,
    validDate: LocalDate?,
) {
    val createState by viewModel.createResult.collectAsState()
    val retractState by viewModel.retractResult.collectAsState()
    val revokeState by viewModel.revokeResult.collectAsState()
    LaunchedEffect(Unit) {
        logInfo("BranchSelectScreen", "invite panel opened for branch $branchId")
        viewModel.loadSent(branchId)
        viewModel.loadAccepted(branchId)
    }
    LaunchedEffect(query, dateText, validDate) {
        // A malformed date (mid-typing) must not fire a search the backend 400s — gate the
        // effect on the parsed date (pass-1 finding).
        if (validDate != null) {
            viewModel.searchCandidates(branchId, query, dateText)
        }
    }
    LaunchedEffect(createState) {
        val error = createState as? UiState.Error
        if (error != null) {
            logWarn("BranchSelectScreen", "sendInvite=Error: ${error.message}")
        }
    }
    LaunchedEffect(retractState) {
        val error = retractState as? UiState.Error
        if (error != null) {
            logWarn("BranchSelectScreen", "retractInvite=Error: ${error.message}")
        }
    }
    LaunchedEffect(revokeState) {
        val error = revokeState as? UiState.Error
        if (error != null) {
            logWarn("BranchSelectScreen", "revokeInvite=Error: ${error.message}")
        }
    }
}

@Composable
private fun InvitePanelForm(
    viewModel: ReliefInviteViewModel,
    state: InvitePanelFormState,
    callbacks: InvitePanelFormCallbacks,
    results: @Composable () -> Unit,
) {
    val createState by viewModel.createResult.collectAsState()
    val retractState by viewModel.retractResult.collectAsState()
    val revokeState by viewModel.revokeResult.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Invite staff for relief",
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = state.dateText,
                onValueChange = callbacks.onDateChange,
                label = { Text("Date (yyyy-MM-dd)") },
                singleLine = true,
                isError = state.dateText.isNotBlank() && state.validDate == null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = callbacks.onQueryChange,
                label = { Text("Search staff by name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            (createState as? UiState.Error)?.message?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            (retractState as? UiState.Error)?.message?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            (revokeState as? UiState.Error)?.message?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            results()
        }
    }
}

@Composable
private fun InvitePanelResults(
    branchId: String,
    viewModel: ReliefInviteViewModel,
    dateText: String,
    validDate: LocalDate?,
) {
    // #377 — the destructive revoke needs an explicit confirm (removes someone's granted
    // access); the tapped row parks here until the dialog resolves it.
    var pendingRevoke by remember { mutableStateOf<ReliefInviteResponse?>(null) }

    val candidatesState by viewModel.candidates.collectAsState()
    // Branch-keyed keep-last mirror (#162 KeepLastByKey — the #160 pass-1/pass-2 cross-branch
    // bleed class is structurally unrenderable: the gate below reads THIS panel's key, so
    // another branch's rows can never pass it; the old commit-stamp machinery is gone).
    val sentByKey by viewModel.sentByKey.collectAsState()
    val acceptedByKey by viewModel.acceptedByKey.collectAsState()
    val createState by viewModel.createResult.collectAsState()
    val retractState by viewModel.retractResult.collectAsState()
    val revokeState by viewModel.revokeResult.collectAsState()
    val lastSent = sentByKey[branchId]
    val lastAccepted = acceptedByKey[branchId]

    val sendBusy = createState is UiState.Loading
    val retractBusy = retractState is UiState.Loading
    val revokeBusy = revokeState is UiState.Loading

    CandidateResults(
        state = candidatesState,
        sendBusy = sendBusy,
        dateValid = validDate != null,
        onInvite = { candidate ->
            if (validDate != null) {
                viewModel.sendInvite(branchId, candidate.id, dateText)
            }
        },
    )

    SentInvitesSection(
        lastSent = lastSent,
        retractBusy = retractBusy,
        onRetract = { inviteId -> viewModel.retractInvite(inviteId, branchId) },
    )

    AcceptedDutiesSection(
        lastAccepted = lastAccepted,
        revokeBusy = revokeBusy,
        onRevokeRequest = { invite -> pendingRevoke = invite },
    )

    pendingRevoke?.let { invite ->
        RevokeDutyConfirmDialog(
            invite = invite,
            busy = revokeBusy,
            onConfirm = {
                viewModel.revokeInvite(invite.id, branchId)
                pendingRevoke = null
            },
            onDismiss = { pendingRevoke = null },
        )
    }
}

@Composable
private fun SentInvitesSection(
    lastSent: List<ReliefInviteResponse>?,
    retractBusy: Boolean,
    onRetract: (String) -> Unit,
) {
    // Branch-gated keep-last (the #160 pass-1/pass-2 HARD class, now by construction): the
    // caller passes THIS panel's keyed mirror entry — null until this branch's first commit, so
    // another branch's rows (with live Retract) can never render here while this panel's load
    // is in flight or failed; a committed entry covers same-branch reloads (Loading/Error keep
    // rendering it).
    if (lastSent == null) return
    val sent = lastSent
    // #399 — past-operational-date PENDING invites render Expired with no Retract (the
    // NotificationsScreen pattern); resolved statuses keep their raw enum text.
    val today = currentOperationalDate()

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = "Sent invites (${sent.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sent.forEach { invite ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${invite.inviteeName} · ${invite.date}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = if (isInviteExpired(invite, today)) "Expired" else invite.status.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isInviteActionable(invite, today)) {
                    TextButton(
                        onClick = { onRetract(invite.id) },
                        enabled = !retractBusy,
                    ) {
                        Text("Retract")
                    }
                }
            }
        }
    }
}

/**
 * #377 — branch-wide ACCEPTED duties (colleagues' included): any active member may revoke
 * until the invitee clocks in. Same keyed-mirror gate as the sent list — null (nothing ever
 * committed; a non-member's 403 never commits) renders nothing, so non-members never see
 * the affordance.
 */
@Composable
private fun AcceptedDutiesSection(
    lastAccepted: List<ReliefInviteResponse>?,
    revokeBusy: Boolean,
    onRevokeRequest: (ReliefInviteResponse) -> Unit,
) {
    if (lastAccepted == null) return
    val accepted = lastAccepted

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = "Accepted relief duties (${accepted.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (accepted.isEmpty()) {
            Text(
                text = "No accepted duties",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        accepted.forEach { invite ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${invite.inviteeName} · ${invite.date}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onRevokeRequest(invite) },
                    enabled = !revokeBusy,
                ) {
                    Text("Revoke")
                }
            }
        }
    }
}
