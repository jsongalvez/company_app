package com.companyb.companyapp.workforce.relief

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.workforce.ReliefAccessResponse
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import com.companyb.companyapp.contracts.workforce.ReliefCandidateResponse
import com.companyb.companyapp.contracts.workforce.ReliefInviteResponse
import com.companyb.companyapp.ui.contract.DestructiveConfirmDialog
import com.companyb.companyapp.ui.contract.FieldErrorText
import com.companyb.companyapp.ui.contract.InlineStatus
import com.companyb.companyapp.ui.contract.InlineStatusKind
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.contract.operationalField
import com.companyb.companyapp.ui.contract.operationalFocusRing
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.PrimaryHover
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import kotlinx.datetime.LocalDate

private const val TAG = "ReliefPlanningSection"

/**
 * #680 — the shared relief-planning owner. Sessions (Team Relief tab) and BranchSelect
 * (the per-card Invite staff shortcut) render the same planning content with explicit
 * branch/date — one implementation, not two. BranchSelect and Sessions supply context
 * (branch, viewed date, VMs) and navigation only.
 *
 * Planning shape per ticket:
 * - header names the branch and the actual chosen date; the picker defaults to the viewed
 *   date when it is an eligible future day, otherwise tomorrow ([defaultPlanningDate]);
 *   typed dates stay available with validation and changing date reruns eligibility and
 *   clears the selected candidate (selection keys on branch+date below);
 * - selecting a candidate only selects — one explicit Send commits, without a further
 *   confirmation. Success stays in the planning context, shows the sent row (the VM
 *   reloads it), clears selection/query, focuses search for the next invitation, and
 *   retains branch/date. Failure retains candidate/date/query and the error (the
 *   same-operation continuity — nothing resets until the next Send);
 * - pending send keeps stable geometry ([PrimaryActionButton]) and blocks duplicate send;
 * - sent/accepted history keeps Retract plus the protected Revoke confirm (person,
 *   branch/date, loss of access named — never an unprotected click); expired/remitted-day
 *   limits stay authoritative ([isInviteActionable]/[isInviteExpired], server messages);
 * - keyed mirrors retain lists across background refresh failures with a section Retry,
 *   kept separate from the action errors so a failed refresh never reads as a failed send;
 * - Requests and Invitations carry separate concise empty states.
 * #729 — scope-explicit labels without merging workflows: the header orients to
 * branch/current day without claiming a master date; Requests names the active
 * operational date, Invite names the selected invitation date (candidate search only),
 * and history names the branch-wide all-dates scope. No state-owner rewrites — the
 * separate state scopes stay the product model.
 */
@Composable
fun ReliefPlanningTabContent(
    branchId: String,
    branchName: String?,
    viewedDate: String?,
    branchDayId: String?,
    inviteViewModel: ReliefInviteViewModel,
    accessViewModel: ReliefAccessViewModel,
    currentUserId: String?,
    isReliefUser: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PlanningBranchDateHeader(branchName = branchName, viewedDate = viewedDate)
        PlanningRequestsSection(
            accessViewModel = accessViewModel,
            branchDayId = branchDayId,
            branchName = branchName,
            activeDate = viewedDate,
            currentUserId = currentUserId,
            isReliefUser = isReliefUser,
        )
        ReliefInvitePlanningContent(
            branchId = branchId,
            branchName = branchName,
            viewedDate = viewedDate,
            inviteViewModel = inviteViewModel,
        )
    }
}

/**
 * #680 — the invite-planning half: date picker, candidate search, select-then-send, and
 * the sent/accepted history. Used by the Sessions Relief tab and the BranchSelect invite
 * shortcut (viewedDate null there — the default resolves to tomorrow).
 */
@Composable
fun ReliefInvitePlanningContent(
    branchId: String,
    branchName: String?,
    viewedDate: String?,
    inviteViewModel: ReliefInviteViewModel,
    modifier: Modifier = Modifier,
) {
    val today = currentOperationalDate()
    var dateText by rememberSaveable(branchId, viewedDate) { mutableStateOf(defaultPlanningDate(viewedDate, today)) }
    var query by rememberSaveable(branchId) { mutableStateOf("") }
    // #680 — the selection survives query edits and tab switches (both legs saveable) and
    // clears only when the parsed date really moves (see the parsed-date effect below).
    var selectedCandidateId by rememberSaveable(branchId) { mutableStateOf<String?>(null) }
    var selectedCandidateLabel by rememberSaveable(branchId) { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    // #680 — search-retry bump: re-issues the current query/date without touching the draft.
    var searchRetry by remember(branchId) { mutableStateOf(0) }
    val searchFocus = remember { FocusRequester() }

    val validDate = parseInviteDate(dateText)
    val dateEligible = isPlanningDateEligible(dateText, today)
    // #680 — the last validly parsed day: a new valid day clears an incompatible selection;
    // transient invalid input mid-typing keeps it.
    var lastParsedDate by remember(branchId) { mutableStateOf(parseInviteDate(defaultPlanningDate(viewedDate, today))) }
    LaunchedEffect(validDate) {
        if (shouldClearPlanningSelection(lastParsedDate, validDate)) {
            selectedCandidateId = null
            selectedCandidateLabel = null
        }
        if (validDate != null) {
            lastParsedDate = validDate
        }
    }

    PlanningInviteEffects(
        inviteViewModel = inviteViewModel,
        branchId = branchId,
        query = query,
        dateText = dateText,
        validDate = validDate,
        searchRetry = searchRetry,
    )

    val candidatesState by inviteViewModel.candidates.collectAsState()
    val createState by inviteViewModel.createResult.collectAsState()
    // #680 — success clears the draft in every case (a stale recapture after a tab switch
    // must not linger as a duplicate-send setup), but focus moves to search only on a live
    // transition — panel/tab entry never steals focus.
    var sendArmed by remember(branchId) { mutableStateOf(createState !is UiState.Success) }
    LaunchedEffect(createState) {
        if (createState is UiState.Success) {
            selectedCandidateId = null
            selectedCandidateLabel = null
            query = ""
            if (sendArmed) {
                searchFocus.requestFocus()
            }
            sendArmed = false
        } else {
            sendArmed = true
        }
    }

    val sendBusy = createState is UiState.Loading
    val canSend = selectedCandidateId != null

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(text = reliefInviteHeading(dateText), style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${reliefPlanningBranchLabel(branchName)} · candidate search only",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
            Text(
                text = "Changing this date does not affect Requests or history.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text("Date (yyyy-MM-dd)") },
                singleLine = true,
                isError = dateText.isNotBlank() && validDate == null,
                modifier = Modifier.operationalField(),
            )
            if (dateText.isNotBlank() && validDate == null) {
                FieldErrorText(message = "Use yyyy-MM-dd, today or later.")
            }
            // #680 — a parseable but past day explains itself (Send stays disabled below).
            if (validDate != null && !dateEligible) {
                FieldErrorText(message = "Past dates can't take new invitations.")
            }
            // #680 — the calendar picker writes the same typed-date state (typed entry stays
            // available with validation above); no range presets.
            TertiaryActionButton(
                label = "Pick date",
                onClick = { pickerOpen = true },
            )
            if (pickerOpen) {
                PlanningDatePickerDialog(
                    currentValue = dateText,
                    onPick = { dateText = it },
                    onDismiss = { pickerOpen = false },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search staff by name") },
                singleLine = true,
                modifier = Modifier.operationalField().focusRequester(searchFocus),
            )

            PlanningCandidateSelection(
                state = candidatesState,
                selectedId = selectedCandidateId,
                selectionEnabled = validDate != null && !sendBusy,
                onSelect = { candidate ->
                    selectedCandidateId = candidate.id
                    selectedCandidateLabel = "${candidate.displayName} (${candidate.username})"
                },
                onClear = {
                    selectedCandidateId = null
                    selectedCandidateLabel = null
                },
                onRetrySearch = { searchRetry++ },
            )

            // #680 — the one explicit Send: stable geometry while pending, duplicate send
            // blocked, and the selected person's identity shown above it.
            if (selectedCandidateId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Selected: ${selectedCandidateLabel ?: "staff"}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TertiaryActionButton(
                        label = "Clear",
                        onClick = {
                            selectedCandidateId = null
                            selectedCandidateLabel = null
                        },
                    )
                }
            }
            PrimaryActionButton(
                label = "Send invitation",
                onClick = {
                    val id = selectedCandidateId
                    if (id != null && validDate != null) {
                        inviteViewModel.sendInvite(branchId, id, dateText)
                    }
                },
                enabled = canSend && dateEligible && !sendBusy,
                isBusy = sendBusy,
            )

            PlanningActionErrors(
                inviteViewModel = inviteViewModel,
                onSendRetry = {
                    val id = selectedCandidateId
                    if (id != null && validDate != null) {
                        inviteViewModel.sendInvite(branchId, id, dateText)
                    }
                },
            )

            PlanningHistoryHeader(branchName = branchName)
            PlanningSentSection(branchId = branchId, inviteViewModel = inviteViewModel)
            PlanningAcceptedSection(branchId = branchId, inviteViewModel = inviteViewModel)
            PlanningInvitationsEmpty(
                branchId = branchId,
                inviteViewModel = inviteViewModel,
            )
        }
    }
}

@Composable
private fun PlanningBranchDateHeader(
    branchName: String?,
    viewedDate: String?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
        Text(
            text = "Relief planning",
            style = MaterialTheme.typography.labelSmall,
            color = InkSubtle,
        )
        Text(
            text = reliefPlanningBranchLabel(branchName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // #729 — the top header orients to branch/current day without claiming a master
        // date: each section below names its own date authority.
        val orientation =
            if (viewedDate.isNullOrBlank()) {
                "Requests use the active day below; invitations use their own date."
            } else {
                "Current day $viewedDate — sections below use their own dates."
            }
        Text(
            text = orientation,
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
    }
}

/**
 * #680 — the Requests section of the planning tab: today's branch-day asks through the
 * existing access card (Grant/Deny/Cancel/Withdraw stay explicit contextual actions).
 * Refresh failures keep the retained list with their own Retry, separate from the action
 * errors the card surfaces; an empty day states so concisely, apart from Invitations.
 * #729 — the heading names the active operational date (never the invite form date) and
 * persists across loading/error/empty so scope never degrades to a generic message.
 * No active branchDayId renders nothing (fail-closed — never fabricate a Requests date).
 */
@Composable
private fun PlanningRequestsSection(
    accessViewModel: ReliefAccessViewModel,
    branchDayId: String?,
    branchName: String?,
    activeDate: String?,
    currentUserId: String?,
    isReliefUser: Boolean,
) {
    val dayId = branchDayId ?: return
    val requestsState by accessViewModel.requests.collectAsState()
    val freshest by accessViewModel.freshestRequests.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = reliefRequestsHeading(activeDate),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
        Text(
            text =
                "Active branch day · ${reliefPlanningBranchLabel(branchName)} — " +
                    "invite date changes do not affect this list.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
        PlanningRequestsBody(
            accessViewModel = accessViewModel,
            dayId = dayId,
            requestsState = requestsState,
            freshest = freshest,
            currentUserId = currentUserId,
            isReliefUser = isReliefUser,
        )
    }
}

/** #729 — the Requests body under the persistent scope heading (loading/error/empty/rows). */
@Composable
private fun PlanningRequestsBody(
    accessViewModel: ReliefAccessViewModel,
    dayId: String,
    requestsState: UiState<List<ReliefAccessResponse>>,
    freshest: List<ReliefAccessResponse>?,
    currentUserId: String?,
    isReliefUser: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (requestsState is UiState.Loading && freshest == null) {
            InlineStatus(message = "Loading requests…", kind = InlineStatusKind.UPDATING)
        }
        (requestsState as? UiState.Error)?.let { error ->
            PlanningLoadError(
                message = error.message,
                onRetry = { accessViewModel.loadRequests(dayId) },
            )
        }
        val rows = freshest
        // #680 — the concise Requests empty state, apart from Invitations: members see it
        // when no incoming ask stands (the card would render nothing for them); relief
        // users see it only on a truly empty day (their outgoing rows render in the card).
        val nothingForMember = !isReliefUser && rows?.incomingPending(currentUserId)?.isEmpty() == true
        // #695 — named empty-state gate: same member/relief distinction, one readable branch.
        val showRequestsEmpty =
            rows != null && requestsState !is UiState.Loading &&
                (rows.isEmpty() || nothingForMember)
        if (showRequestsEmpty) {
            Text(
                text = PLANNING_EMPTY_REQUESTS,
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        } else {
            ReliefAccessCard(
                viewModel = accessViewModel,
                branchDayId = dayId,
                currentUserId = currentUserId,
                isReliefUser = isReliefUser,
            )
        }
    }
}

@Composable
private fun PlanningInviteEffects(
    inviteViewModel: ReliefInviteViewModel,
    branchId: String,
    query: String,
    dateText: String,
    validDate: LocalDate?,
    searchRetry: Int,
) {
    val createState by inviteViewModel.createResult.collectAsState()
    val retractState by inviteViewModel.retractResult.collectAsState()
    val revokeState by inviteViewModel.revokeResult.collectAsState()
    LaunchedEffect(branchId) {
        logInfo(TAG, "planning opened for branch $branchId")
        inviteViewModel.clearPlanningLoadErrors()
        inviteViewModel.loadSent(branchId)
        inviteViewModel.loadAccepted(branchId)
    }
    LaunchedEffect(branchId, query, dateText, validDate, searchRetry) {
        // A malformed date (mid-typing) must not fire a search the backend 400s — gate the
        // effect on the parsed date. The VM's generation guard makes a superseded landing
        // (branch/date switch mid-flight) inert.
        if (validDate != null) {
            inviteViewModel.searchCandidates(branchId, query, dateText)
        }
    }
    LaunchedEffect(createState) {
        (createState as? UiState.Error)?.let { logWarn(TAG, "sendInvite=Error: ${it.message}") }
    }
    LaunchedEffect(retractState) {
        (retractState as? UiState.Error)?.let { logWarn(TAG, "retractInvite=Error: ${it.message}") }
    }
    LaunchedEffect(revokeState) {
        (revokeState as? UiState.Error)?.let { logWarn(TAG, "revokeInvite=Error: ${it.message}") }
    }
}

/** #680 — candidate search states: selecting only selects; the explicit Send commits. */
@Composable
private fun PlanningCandidateSelection(
    state: UiState<List<ReliefCandidateResponse>>,
    selectedId: String?,
    selectionEnabled: Boolean,
    onSelect: (ReliefCandidateResponse) -> Unit,
    onClear: () -> Unit,
    onRetrySearch: () -> Unit,
) {
    when (state) {
        is UiState.Idle -> {}

        is UiState.Loading -> {
            InlineStatus(message = "Searching…", kind = InlineStatusKind.UPDATING)
        }

        is UiState.Error -> {
            PlanningLoadError(message = state.message, onRetry = onRetrySearch)
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
                        PlanningCandidateRow(
                            candidate = candidate,
                            selected = candidate.id == selectedId,
                            selectionEnabled = selectionEnabled,
                            onSelect = { onSelect(candidate) },
                            onClear = onClear,
                        )
                    }
                }
            }
        }
    }
}

/** #680 — one selectable candidate row: identity plus Select/Selected, never tap-to-send. */
@Composable
private fun PlanningCandidateRow(
    candidate: ReliefCandidateResponse,
    selected: Boolean,
    selectionEnabled: Boolean,
    onSelect: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = selectionEnabled) {
                    if (selected) onClear() else onSelect()
                }.rowHover(enabled = selectionEnabled)
                .operationalFocusRing()
                .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = candidate.username,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // #670 — stable trailing slot: the label persists so row bounds never shift.
        Text(
            text = if (selected) "Selected ✓" else "Select",
            style = MaterialTheme.typography.labelMedium,
            color =
                if (selected) {
                    PrimaryHover
                } else if (selectionEnabled) {
                    PrimaryHover
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/** #680 — action failures inline (send/retract/revoke), apart from refresh-failure Retries. */
@Composable
private fun PlanningActionErrors(
    inviteViewModel: ReliefInviteViewModel,
    onSendRetry: () -> Unit,
) {
    val createState by inviteViewModel.createResult.collectAsState()
    val retractState by inviteViewModel.retractResult.collectAsState()
    val revokeState by inviteViewModel.revokeResult.collectAsState()

    // #680 — the failed Send keeps its recovery next to the error: re-tapping re-issues the
    // same branch/candidate/date operation (the form retains all three until the next Send).
    (createState as? UiState.Error)?.message?.let { error ->
        PlanningLoadError(message = error, onRetry = onSendRetry)
    }
    (retractState as? UiState.Error)?.message?.let { error ->
        PlanningLoadError(message = error, onRetry = null)
    }
    (revokeState as? UiState.Error)?.message?.let { error ->
        PlanningLoadError(message = error, onRetry = null)
    }
}

/**
 * #680 — one refresh/action failure strip with its screen-level warn log (the
 * AttendanceRosterSection RosterLoadError shape): lists stay retained above it, and the
 * Retry re-issues only its own leg.
 */
@Composable
private fun PlanningLoadError(
    message: String,
    onRetry: (() -> Unit)?,
    retryLabel: String = "Retry",
) {
    LaunchedEffect(message) {
        logWarn(TAG, "planning load/action error: $message")
    }
    InlineStatus(
        message = message,
        kind = InlineStatusKind.FAILURE,
        onRetry = onRetry,
        retryLabel = retryLabel,
    )
}

/**
 * #729 — the branch-wide history scope header. Renders always (above the sent/accepted
 * mirrors) so loading/error/empty never degrade to an ambiguous generic message, and
 * establishes that the lists span all dates and are not filtered by the invite form date.
 * Rows keep their own date below.
 */
@Composable
private fun PlanningHistoryHeader(branchName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = reliefHistoryHeading(branchName),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Not filtered by the invite date above — each row shows its own date.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
    }
}

@Composable
private fun PlanningSentSection(
    branchId: String,
    inviteViewModel: ReliefInviteViewModel,
) {
    // Branch-gated keep-last (the #160 pass-1/pass-2 class, #162 KeepLastByKey — now by
    // construction): null until this branch's first commit, so another branch's rows (with
    // live Retract) can never render here while this panel's load is in flight or failed.
    val sentByKey by inviteViewModel.sentByKey.collectAsState()
    val sentError by inviteViewModel.sentLoadError.collectAsState()
    val retractState by inviteViewModel.retractResult.collectAsState()

    // #680 — the refresh-failure strip renders above the mirror gate so a cold first-load
    // failure still offers its Retry (the mirror stays null until a commit lands).
    sentError?.let { error ->
        PlanningLoadError(
            message = error,
            onRetry = { inviteViewModel.loadSent(branchId) },
        )
    }
    val lastSent = sentByKey[branchId] ?: return
    // #399 — past-operational-date PENDING invites render Expired with no Retract;
    // resolved statuses keep their raw enum text.
    val today = currentOperationalDate()
    val retractBusy = retractState is UiState.Loading

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = "Sent invites (${lastSent.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lastSent.forEach { invite ->
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
                    TertiaryActionButton(
                        label = "Retract",
                        onClick = { inviteViewModel.retractInvite(invite.id, branchId) },
                        enabled = !retractBusy,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanningAcceptedSection(
    branchId: String,
    inviteViewModel: ReliefInviteViewModel,
) {
    var pendingRevoke by remember(branchId) { mutableStateOf<ReliefInviteResponse?>(null) }
    var revokeWasLoading by remember(branchId) { mutableStateOf(false) }

    // Same keyed-mirror gate as the sent list (#160/#162) — null (nothing ever committed; a
    // non-member's 403 never commits) renders nothing, so non-members never see it.
    val acceptedByKey by inviteViewModel.acceptedByKey.collectAsState()
    val acceptedError by inviteViewModel.acceptedLoadError.collectAsState()
    val revokeState by inviteViewModel.revokeResult.collectAsState()

    // #680 — like the sent leg, the refresh-failure strip renders above the gate.
    acceptedError?.let { error ->
        PlanningLoadError(
            message = error,
            onRetry = { inviteViewModel.loadAccepted(branchId) },
        )
    }
    val lastAccepted = acceptedByKey[branchId] ?: return
    val revokeBusy = revokeState is UiState.Loading

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = "Accepted relief duties (${lastAccepted.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (lastAccepted.isEmpty()) {
            Text(
                text = "No accepted duties",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        lastAccepted.forEach { invite ->
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
                TertiaryActionButton(
                    label = "Revoke",
                    onClick = { pendingRevoke = invite },
                    enabled = !revokeBusy,
                )
            }
        }
    }

    pendingRevoke?.let { invite ->
        PlanningRevokeConfirmDialog(
            invite = invite,
            busy = revokeBusy,
            onConfirm = { inviteViewModel.revokeInvite(invite.id, branchId) },
            onDismiss = { pendingRevoke = null },
        )
    }
    LaunchedEffect(revokeState) {
        // #695 — named settle gate: success, error, or an idle after loading ends the revoke wait.
        val revokeSettled =
            revokeState is UiState.Success || revokeState is UiState.Error ||
                (revokeState is UiState.Idle && revokeWasLoading)
        if (revokeState is UiState.Loading) {
            revokeWasLoading = true
        } else if (revokeSettled) {
            revokeWasLoading = false
            pendingRevoke = null
        }
    }
}

/** #680 — the Invitations empty state, separate from Requests: the sent leg loaded empty
 * while the accepted leg is settled empty or expectedly absent (a non-member's 403 never
 * commits by design, so it must not starve the empty state). */
@Composable
private fun PlanningInvitationsEmpty(
    branchId: String,
    inviteViewModel: ReliefInviteViewModel,
) {
    val sentByKey by inviteViewModel.sentByKey.collectAsState()
    val acceptedByKey by inviteViewModel.acceptedByKey.collectAsState()
    val acceptedError by inviteViewModel.acceptedLoadError.collectAsState()
    val sent = sentByKey[branchId]
    val accepted = acceptedByKey[branchId]
    val acceptedSettled = (accepted != null && accepted.isEmpty()) || (accepted == null && acceptedError != null)
    if (sent != null && sent.isEmpty() && acceptedSettled) {
        Text(
            text = PLANNING_EMPTY_INVITATIONS,
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
    }
}

/** Destructive-action confirm (#377, #680): revocation removes someone's granted access. */
@Composable
internal fun PlanningRevokeConfirmDialog(
    invite: ReliefInviteResponse,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DestructiveConfirmDialog(
        title = "Revoke relief duty?",
        body =
            "${invite.inviteeName}'s duty at ${invite.branchName} on ${invite.date} " +
                "will be revoked and their access removed.",
        confirmLabel = "Revoke",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isBusy = busy,
    )
}

/**
 * #680 — the calendar picker for the invite date (the remittance-picker precedent):
 * Material3 DatePickerDialog writing the same typed-date state — typed entry stays
 * available with validation, and there are no range presets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanningDatePickerDialog(
    currentValue: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = reliefIsoToPickerMillis(currentValue))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    reliefPickerMillisToIso(pickerState.selectedDateMillis)?.let(onPick)
                    onDismiss()
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/**
 * #680 — the deep-link day's read-only sections, shared with the planning tab's language:
 * the same Expired rule and status text, the same separate empty states, the same per-leg
 * Retry — with no actions (view-only relief users stay view-only).
 */
@Composable
fun PlanningDayRequests(
    state: UiState<List<ReliefAccessResponse>>,
    dayPast: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        when (state) {
            is UiState.Loading -> {
                Text("Loading…", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            }

            is UiState.Error -> {
                PlanningLoadError(message = state.message, onRetry = onRetry)
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Text(
                        PLANNING_EMPTY_REQUESTS,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                } else {
                    state.data.forEach { row -> PlanningDayRequestRow(row, dayPast) }
                }
            }

            UiState.Idle -> {}
        }
    }
}

@Composable
fun PlanningDayInvites(
    state: UiState<List<ReliefInviteResponse>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        when (state) {
            is UiState.Error -> {
                PlanningLoadError(message = state.message, onRetry = onRetry)
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Text(
                        PLANNING_EMPTY_INVITATIONS,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                } else {
                    val today = currentOperationalDate()
                    toReliefDayInviteRows(state.data, today).forEach { row -> PlanningDayInviteRow(row) }
                }
            }

            is UiState.Loading -> {
                Text("Loading…", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            }

            UiState.Idle -> {}
        }
    }
}

@Composable
private fun PlanningDayInviteRow(row: ReliefDayInviteRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = row.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(text = row.statusText, style = MaterialTheme.typography.labelSmall, color = InkSubtle)
    }
}

@Composable
private fun PlanningDayRequestRow(
    row: ReliefAccessResponse,
    dayPast: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // #666 — label with the requester display name; the mine list ships null
        // requesterName (the caller is the requester), so fall back to the id.
        Text(
            text = reliefRequestRowLabel(row),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        val expired = dayPast && row.requestStatus == ReliefAccessStatus.PENDING
        Text(
            text = if (expired) "Expired" else row.requestStatus.name,
            style = MaterialTheme.typography.labelSmall,
            color = InkSubtle,
        )
    }
}

/** #680 — the Requests empty copy, shared by the planning tab and the day view. */
const val PLANNING_EMPTY_REQUESTS = "No requests"

/** #680 — the Invitations empty copy, shared by the planning tab and the day view. */
const val PLANNING_EMPTY_INVITATIONS = "No invitations"
