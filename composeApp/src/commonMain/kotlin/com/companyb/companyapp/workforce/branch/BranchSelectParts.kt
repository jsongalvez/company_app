package com.companyb.companyapp.workforce.branch

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branch.BranchClockInStatus
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.branch.MeBranchResponse
import com.companyb.companyapp.contracts.workforce.ReliefCandidateResponse
import com.companyb.companyapp.contracts.workforce.ReliefInviteResponse
import com.companyb.companyapp.ui.contract.DestructiveConfirmDialog
import com.companyb.companyapp.ui.contract.InlineStatus
import com.companyb.companyapp.ui.contract.InlineStatusKind
import com.companyb.companyapp.ui.contract.OperationalUiContract
import com.companyb.companyapp.ui.contract.PageHeading
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.SecondaryActionButton
import com.companyb.companyapp.ui.contract.SecondaryLabel
import com.companyb.companyapp.ui.contract.operationalFocusRing
import com.companyb.companyapp.ui.theme.PrimaryHover
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.workforce.relief.currentOperationalDate
import kotlinx.datetime.plus

/** Destructive-action confirm (#377): revocation removes someone's granted access. */
@Composable
internal fun RevokeDutyConfirmDialog(
    invite: ReliefInviteResponse,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // #670 — destructive confirm: safe-action initial focus, Escape/Back pinned while busy.
    // The label stays constant (stable geometry lives in the shared shell's reserved slot).
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
 * Default invite date = the day after the operational date (Asia/Manila, #399 clock) —
 * future-day planning is the use case; before 04:00 the still-open calendar day qualifies.
 */
internal fun defaultInviteDate(): String =
    currentOperationalDate().plus(1, kotlinx.datetime.DateTimeUnit.DAY).toString()

/**
 * Status label per spec line 185: "Clocked in here" / "Clocked in elsewhere" /
 * "Not clocked in" / "Relief duty". Relief duty replaces the here-label when the clock-in
 * was as relief (isRelief — not assigned to this branch, #98). NOT_CLOCKED_IN carries the
 * muted label alongside the Clock In button (the button is the action; the label is the
 * state per spec).
 */
internal fun statusLabel(branch: MeBranchResponse): String =
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

/** Branch card identity block: name, type, and clock-in status lines. */
@Composable
internal fun BranchCardInfo(
    branch: MeBranchResponse,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = branch.branchName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text =
                when (branch.branchType) {
                    BranchType.CLINIC -> "Clinic"
                    BranchType.PROVINCIAL_TOUR -> "Provincial Tour"
                    BranchType.MEDICAL_MISSION -> "Medical Mission"
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = statusLabel(branch),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (branch.clockInStatus == BranchClockInStatus.NOT_CLOCKED_IN) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    // #670 — Primary undershoots 4.5:1 at 12sp; PrimaryHover clears AA.
                    PrimaryHover
                },
        )
    }
}

/** Clock-in action with stable pending geometry; rendered only for NOT_CLOCKED_IN branches. */
@Composable
internal fun BranchClockInButton(
    isClockingIn: Boolean,
    canClockIn: Boolean,
    onClockIn: () -> Unit,
) {
    // #670 — label + bounds persist while busy; duplicate submission disabled.
    PrimaryActionButton(
        label = "Clock In",
        onClick = onClockIn,
        enabled = canClockIn,
        isBusy = isClockingIn,
    )
}

/**
 * #669 — only an already-clocked-here row offers resume: the server read restores
 * whatever shift stands open (always the HERE branch when one exists), so an ELSEWHERE
 * row needs no action of its own and NOT_CLOCKED_IN keeps Clock In.
 */
internal fun showContinueFor(status: BranchClockInStatus): Boolean = status == BranchClockInStatus.CLOCKED_IN_HERE

/** #669 — resume action for an already-clocked-here row: the launch/login resolver, never a second clock-in. */
@Composable
internal fun BranchContinueButton(
    branchName: String,
    busy: Boolean,
    enabled: Boolean,
    onContinue: () -> Unit,
) {
    PrimaryActionButton(
        label = "Continue at $branchName",
        onClick = onContinue,
        enabled = enabled,
        isBusy = busy,
    )
}

/** Single candidate row: identity plus Invite affordance gated on a valid date. */
@Composable
internal fun CandidateRow(
    candidate: ReliefCandidateResponse,
    dateValid: Boolean,
    sendBusy: Boolean,
    onInvite: (ReliefCandidateResponse) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = dateValid && !sendBusy) {
                    onInvite(candidate)
                }.rowHover(enabled = dateValid && !sendBusy)
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
        // #670 — stable trailing slot: the 18dp spinner reserves beside a persistent Invite
        // label instead of replacing it, so row bounds never shift while sending.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(OperationalUiContract.progressSlot),
                contentAlignment = Alignment.Center,
            ) {
                if (sendBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(OperationalUiContract.progressSlot),
                        strokeWidth = OperationalUiContract.focusRingWidth,
                    )
                }
            }
            Text(
                text = "Invite",
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (dateValid && !sendBusy) {
                        PrimaryHover
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

/** Branch-select title block: heading plus clock-in subtitle. */
@Composable
internal fun BranchSelectHeader() {
    // #670 — page heading owns the hierarchy; subtitle recedes as secondary text.
    PageHeading(text = "Select branch")
    Spacer(modifier = Modifier.height(Spacing.sm))
    SecondaryLabel(text = "Clock in to start your day")
    Spacer(modifier = Modifier.height(Spacing.sm))
}

/** Branch-list error banners: send failure plus refresh failure with refresh retry. */
@Composable
internal fun BranchErrorBanners(
    clockInError: String?,
    refreshError: String?,
    restoreError: String?,
    onRetryRefresh: () -> Unit,
    onRetryRestore: () -> Unit,
) {
    clockInError?.let { error ->
        InlineStatus(message = error, kind = InlineStatusKind.FAILURE)
        Spacer(modifier = Modifier.height(Spacing.sm))
    }
    refreshError?.let { error ->
        InlineStatus(
            message = "$error — you're already clocked in.",
            kind = InlineStatusKind.FAILURE,
            onRetry = onRetryRefresh,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
    }
    restoreError?.let { error ->
        InlineStatus(
            message = error,
            kind = InlineStatusKind.FAILURE,
            onRetry = onRetryRestore,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
    }
}

/** Branch-list load failure: message plus reload retry. */
@Composable
internal fun BranchLoadErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    logWarn("BranchSelectScreen", "branchesState=Error: $message")
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            InlineStatus(message = message, kind = InlineStatusKind.FAILURE)
            Spacer(modifier = Modifier.height(Spacing.md))
            SecondaryActionButton(label = "Retry", onClick = onRetry)
        }
    }
}

/** Candidate search states: spinner, error, empty, or tappable candidate rows. */
@Composable
internal fun CandidateResults(
    state: UiState<List<ReliefCandidateResponse>>,
    sendBusy: Boolean,
    dateValid: Boolean,
    onInvite: (ReliefCandidateResponse) -> Unit,
) {
    when (state) {
        is UiState.Idle -> {}

        is UiState.Loading -> {
            InlineStatus(message = "Searching…", kind = InlineStatusKind.UPDATING)
        }

        is UiState.Error -> {
            InlineStatus(message = state.message, kind = InlineStatusKind.FAILURE)
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
                        CandidateRow(
                            candidate = candidate,
                            dateValid = dateValid,
                            sendBusy = sendBusy,
                            onInvite = onInvite,
                        )
                    }
                }
            }
        }
    }
}
