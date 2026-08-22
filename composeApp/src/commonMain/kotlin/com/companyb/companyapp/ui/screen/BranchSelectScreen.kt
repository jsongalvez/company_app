package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.clickable
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
import com.companyb.companyapp.domain.BranchClockInStatus
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.ReliefCandidateResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.BranchSelectViewModel
import com.companyb.companyapp.viewmodel.ReliefAccessViewModel
import com.companyb.companyapp.viewmodel.ReliefInviteViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlinx.datetime.plus

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
    var inviteBranchId by remember { mutableStateOf<String?>(null) }

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
        Spacer(modifier = Modifier.height(Spacing.sm))

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
                                inviteExpanded = inviteBranchId == branch.branchId,
                                onToggleInvite = {
                                    inviteBranchId =
                                        if (inviteBranchId == branch.branchId) null else branch.branchId
                                },
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
    inviteExpanded: Boolean,
    onToggleInvite: () -> Unit,
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
            TextButton(onClick = onToggleInvite) {
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
    val candidatesState by viewModel.candidates.collectAsState()
    // Branch-keyed keep-last mirror (#162 KeepLastByKey — the #160 pass-1/pass-2 cross-branch
    // bleed class is structurally unrenderable: the gate below reads THIS panel's key, so
    // another branch's rows can never pass it; the old commit-stamp machinery is gone).
    val sentByKey by viewModel.sentByKey.collectAsState()
    val createState by viewModel.createResult.collectAsState()
    val retractState by viewModel.retractResult.collectAsState()

    var dateText by remember { mutableStateOf(defaultInviteDate()) }
    var query by remember { mutableStateOf("") }

    val createError = (createState as? UiState.Error)?.message
    val retractError = (retractState as? UiState.Error)?.message
    val sendBusy = createState is UiState.Loading
    val retractBusy = retractState is UiState.Loading
    val validDate = parseInviteDate(dateText)

    LaunchedEffect(Unit) {
        logInfo("BranchSelectScreen", "invite panel opened for branch $branchId")
        viewModel.loadSent(branchId)
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
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text("Date (yyyy-MM-dd)") },
                singleLine = true,
                isError = dateText.isNotBlank() && validDate == null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search staff by name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            createError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            retractError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

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
                lastSent = sentByKey[branchId],
                retractBusy = retractBusy,
                onRetract = { inviteId -> viewModel.retractInvite(inviteId, branchId) },
            )
        }
    }
}

@Composable
private fun CandidateResults(
    state: UiState<List<ReliefCandidateResponse>>,
    sendBusy: Boolean,
    dateValid: Boolean,
    onInvite: (ReliefCandidateResponse) -> Unit,
) {
    when (state) {
        is UiState.Idle -> {}

        is UiState.Loading -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "Searching…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is UiState.Error -> {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is UiState.Success -> {
            if (state.data.isEmpty()) {
                Text(
                    text = "No candidates",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    state.data.forEach { candidate ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = dateValid && !sendBusy) {
                                        onInvite(candidate)
                                    }.padding(vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = candidate.username,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (sendBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = "Invite",
                                    style = MaterialTheme.typography.labelMedium,
                                    color =
                                        if (dateValid) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
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
                        text = invite.status.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (invite.status == ReliefInviteStatus.PENDING) {
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

/** Default invite date = tomorrow (Asia/Manila) — future-day planning is the use case. */
private fun defaultInviteDate(): String = manilaToday().plus(1, kotlinx.datetime.DateTimeUnit.DAY).toString()

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
