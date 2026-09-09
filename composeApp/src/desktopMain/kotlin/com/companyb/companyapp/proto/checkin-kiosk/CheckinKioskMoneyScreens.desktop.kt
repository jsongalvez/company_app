package com.companyb.companyapp.proto.checkinkiosk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #782 — checkin-kiosk till: SESSION + PRODUCT remittance, snapshots, 48h undo, commission note.

@Composable
fun CheckinTill(repo: CheckinFakeRepo) {
    CheckinHeading("The till.", 44)
    CheckinSub("Today's drawer, sealed in snapshots. Drafts overlap freely; submits freeze.")
    CheckinNoteCard("Done today: ₱${repo.dayCompletedTotal()} banked across ${repo.sessions.count { it.branchId == repo.currentBranchId && it.status == CheckinSessionStatus.COMPLETED }} completed sessions at ${repo.currentBranch.name}.")
    CheckinRemitKind.entries.forEach { kind ->
        val remit = repo.remittances.first { it.kind == kind }
        CheckinPanel {
            Text(
                if (kind == CheckinRemitKind.SESSION) "Session drawer" else "Product shelf",
                color = CheckinColors.Cream,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (kind == CheckinRemitKind.SESSION) "Session income after hands + costs" else "Unit price × bottles + balms",
                color = CheckinColors.FaintOnNight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "₱${if (remit.state == CheckinRemitState.SUBMITTED) remit.snapshotTotal else remit.draftTotal} · ${remit.state}",
                color = CheckinColors.Marigold,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
            )
            if (remit.snapshotId != null) {
                Text("Snapshot ${remit.snapshotId} · sealed ${remit.submittedAt}", color = CheckinColors.FaintOnNight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            if (remit.state == CheckinRemitState.DRAFT) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        CheckinGhostButton("− ₱500") { repo.adjustDraft(kind, -500) }
                    }
                    Box(Modifier.weight(1f)) {
                        CheckinGhostButton("+ ₱500") { repo.adjustDraft(kind, 500) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                var submitError by remember(kind) { mutableStateOf<String?>(null) }
                CheckinBigButton("Seal it — submit") {
                    submitError = repo.submitRemittance(kind)
                }
                if (submitError != null) {
                    Spacer(Modifier.height(8.dp))
                    CheckinNoteCard(submitError!!)
                }
            } else {
                var undoReason by remember(kind) { mutableStateOf("") }
                var undoError by remember(kind) { mutableStateOf<String?>(null) }
                OutlinedTextField(
                    value = undoReason,
                    onValueChange = { undoReason = it },
                    label = { Text("Undo reason (required, 48h window)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = CheckinColors.Cream,
                        unfocusedTextColor = CheckinColors.Cream,
                        focusedLabelColor = CheckinColors.Marigold,
                        unfocusedLabelColor = CheckinColors.FaintOnNight,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                CheckinGhostButton("Reopen — undo ${remit.snapshotId}") {
                    undoError = repo.undoRemittance(kind, undoReason)
                    if (undoError == null) undoReason = ""
                }
                if (undoError != null) {
                    Spacer(Modifier.height(8.dp))
                    CheckinNoteCard(undoError!!)
                } else {
                    Spacer(Modifier.height(8.dp))
                    CheckinNoteCard("Snapshots are permanent after 48 hours. Undo returns the flow to Draft, unlocks the days, and deletes the snapshot — with your reason in the ledger.")
                }
            }
        }
    }
    CheckinPanel {
        Text("How hands get paid", color = CheckinColors.Cream, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            "Commission pools per branch day and splits evenly across everyone clocked in when the session sold. Relief hands are paid from this branch's drawer, not their home one.",
            color = CheckinColors.FaintOnNight,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
