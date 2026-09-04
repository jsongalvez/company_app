package com.companyb.companyapp.ui.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.companyb.companyapp.domain.BranchClockInStatus
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
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
