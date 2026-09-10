package com.companyb.companyapp.proto.bottomdock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #810 — bottom-dock till: SESSION + PRODUCT remittance, snapshots, 48h undo, commission note.

@Composable
fun DockTill(repo: DockFakeRepo) {
    DockHeading("The till.", 34)
    DockSub("Today's drawer, sealed in snapshots. Drafts overlap freely; submits freeze.")
    Spacer(Modifier.height(12.dp))
    DockNoteCard(
        "Done today: ₱${repo.dayCompletedTotal()} banked across ${repo.sessions.count {
            it.branchId == repo.currentBranchId && it.status == DockSessionStatus.COMPLETED
        }} completed sessions at ${repo.currentBranch.name}.",
    )
    Spacer(Modifier.height(12.dp))
    DockRemitKind.entries.forEach { kind ->
        val remit = repo.remittances.first { it.kind == kind }
        Column {
            DockWindow(title = if (kind == DockRemitKind.SESSION) "Session drawer" else "Product shelf") {
                Text(
                    if (kind ==
                        DockRemitKind.SESSION
                    ) {
                        "Session income after hands + costs"
                    } else {
                        "Unit price × bottles + balms"
                    },
                    color = DockColors.InkSoft,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "₱${if (remit.state == DockRemitState.SUBMITTED) remit.snapshotTotal else remit.draftTotal} · ${remit.state}",
                    color = DockColors.Grape,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                )
                if (remit.snapshotId != null) {
                    Text(
                        "Snapshot ${remit.snapshotId} · sealed ${remit.submittedAt}",
                        color = DockColors.InkSoft,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (remit.state == DockRemitState.DRAFT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            DockGhostButton("− ₱500") { repo.adjustDraft(kind, -500) }
                        }
                        Box(Modifier.weight(1f)) {
                            DockGhostButton("+ ₱500") { repo.adjustDraft(kind, 500) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    var submitError by remember(kind) { mutableStateOf<String?>(null) }
                    DockBigButton("Seal it — submit") {
                        submitError = repo.submitRemittance(kind)
                    }
                    if (submitError != null) {
                        Spacer(Modifier.height(8.dp))
                        DockNoteCard(submitError!!)
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
                    )
                    Spacer(Modifier.height(8.dp))
                    DockGhostButton("Reopen — undo ${remit.snapshotId}") {
                        undoError = repo.undoRemittance(kind, undoReason)
                        if (undoError == null) undoReason = ""
                    }
                    if (undoError != null) {
                        Spacer(Modifier.height(8.dp))
                        DockNoteCard(undoError!!)
                    } else {
                        Spacer(Modifier.height(8.dp))
                        DockNoteCard(
                            "Snapshots are permanent after 48 hours. Undo returns the drawer to Draft and deletes the snapshot — with your reason in the ledger.",
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    DockWindow(title = "How hands get paid") {
        Text(
            "Commission pools per branch day and splits evenly across everyone clocked in when the session sold. Relief hands are paid from this branch's drawer, not their home one.",
            color = DockColors.InkSoft,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
