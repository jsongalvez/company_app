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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.BranchClockInStatus
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.ReliefCandidateResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.logWarn
import kotlinx.datetime.plus

/** Destructive-action confirm (#377): revocation removes someone's granted access. */
@Composable
internal fun RevokeDutyConfirmDialog(
    invite: ReliefInviteResponse,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revoke relief duty?") },
        text = {
            Text(
                "${invite.inviteeName}'s duty at ${invite.branchName} on ${invite.date} " +
                    "will be revoked and their access removed.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy,
            ) {
                Text("Revoke")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
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
                    MaterialTheme.colorScheme.primary
                },
        )
    }
}

/** Clock-in action with busy spinner; rendered only for NOT_CLOCKED_IN branches. */
@Composable
internal fun BranchClockInButton(
    isClockingIn: Boolean,
    canClockIn: Boolean,
    onClockIn: () -> Unit,
) {
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
                .padding(vertical = Spacing.xs),
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

/** Branch-select title block: heading plus clock-in subtitle. */
@Composable
internal fun BranchSelectHeader() {
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
}

/** Branch-list error banners: send failure plus refresh failure with refresh retry. */
@Composable
internal fun BranchErrorBanners(
    clockInError: String?,
    refreshError: String?,
    onRetryRefresh: () -> Unit,
) {
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
        OutlinedButton(onClick = onRetryRefresh) {
            Text("Retry")
        }
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
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
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
