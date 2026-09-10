package com.companyb.companyapp.proto.ownerdesk

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

// #818 — owner-desk vault: SESSION + PRODUCT remittance, snapshots, 48h undo, commission note.

@Composable
fun OwnerVault(repo: OwnerFakeRepo) {
    OwnerHeading("The vault.", 34)
    OwnerSub("The day's takings, sealed under wax. Drafts overlap freely; submits freeze.")
    Spacer(Modifier.height(12.dp))
    OwnerNoteCard(
        "Counted today: ₱${repo.dayCompletedTotal()} banked across ${repo.sessions.count {
            it.branchId == repo.currentBranchId && it.status == OwnerSessionStatus.COMPLETED
        }} completed folios at ${repo.currentBranch.name}.",
    )
    Spacer(Modifier.height(12.dp))
    OwnerRemitKind.entries.forEach { kind ->
        val remit = repo.remittances.first { it.kind == kind }
        Column {
            OwnerLedger(
                title = if (kind == OwnerRemitKind.SESSION) "Session strongbox" else "Product cabinet",
                subtitle =
                    if (kind == OwnerRemitKind.SESSION) "Session income, hands + costs" else "Units, bottles, balms",
                seal = remit.state.name,
            ) {
                Text(
                    "₱${if (remit.state == OwnerRemitState.SUBMITTED) remit.snapshotTotal else remit.draftTotal}",
                    color = OwnerColors.Money,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = OwnerSerif,
                )
                if (remit.snapshotId != null) {
                    Text(
                        "Seal ${remit.snapshotId} · waxed ${remit.submittedAt}",
                        color = OwnerColors.InkSoft,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (remit.state == OwnerRemitState.DRAFT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            OwnerGhostButton("− ₱500") { repo.adjustDraft(kind, -500) }
                        }
                        Box(Modifier.weight(1f)) {
                            OwnerGhostButton("+ ₱500") { repo.adjustDraft(kind, 500) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    var submitError by remember(kind) { mutableStateOf<String?>(null) }
                    OwnerBigButton("Press the seal — submit") {
                        submitError = repo.submitRemittance(kind)
                    }
                    if (submitError != null) {
                        Spacer(Modifier.height(8.dp))
                        OwnerPaperNote(submitError!!)
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
                    OwnerGhostButton("Break the seal — undo ${remit.snapshotId}") {
                        undoError = repo.undoRemittance(kind, undoReason)
                        if (undoError == null) undoReason = ""
                    }
                    if (undoError != null) {
                        Spacer(Modifier.height(8.dp))
                        OwnerPaperNote(undoError!!)
                    } else {
                        Spacer(Modifier.height(8.dp))
                        OwnerPaperNote(
                            "Seals hold for 48 hours. Undo returns the drawer to Draft and melts the seal — " +
                                "with your reason entered in the daybook.",
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    OwnerLedger(title = "How hands get paid") {
        Text(
            "Commission pools per branch day and splits evenly across everyone clocked in when the session sold. " +
                "Relief hands are paid from this house's vault, not their home one.",
            color = OwnerColors.InkSoft,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
