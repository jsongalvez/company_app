package com.companyb.companyapp.proto.inboxzero

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #856 — inbox-zero finance stage: independent SESSION + PRODUCT drafts,
// submit seals an immutable snapshot, Undo returns to draft within 48h.

@Composable
fun IzMoney(repo: IzFakeRepo, focusId: String?) {
    var undoReason by remember { mutableStateOf("") }
    val ordered = remember(repo.version) {
        val focused = repo.remittances.firstOrNull { it.id == focusId }
        if (focused == null) repo.remittances.toList() else listOf(focused) + repo.remittances.filter { it.id != focusId }
    }
    Column(Modifier.fillMaxWidth()) {
        IzSection(
            title = "Remittance drafts",
            note = "SESSION and PRODUCT flow independently; drafts may overlap",
            actions = {
                IzChip("New SESSION draft", onClick = { repo.newDraft(IzRemitKind.SESSION) })
                Spacer(Modifier.width(8.dp))
                IzChip("New PRODUCT draft", onClick = { repo.newDraft(IzRemitKind.PRODUCT) }, primary = true)
            },
        ) {
            ordered.forEach { r ->
                IzCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${r.id} · ${r.kind.label} · ${izPeso(r.amount)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = IzColors.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        IzPill(
                            r.state.label,
                            when (r.state) {
                                IzRemitState.DRAFT -> IzTone.ORANGE
                                IzRemitState.SUBMITTED -> IzTone.GREEN
                                IzRemitState.UNDONE -> IzTone.GREY
                            },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(r.branchDay, fontSize = 12.sp, color = IzColors.Dim)
                    if (r.snapshotId != null) IzKey("Snapshot", "${r.snapshotId} · immutable; later edits never rewrite it")
                    if (r.undoReason != null) IzKey("Undo reason", r.undoReason!!)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.state == IzRemitState.DRAFT) {
                            IzChip("Submit (seal snapshot)", onClick = { repo.submitRemit(r) }, primary = true)
                        }
                        if (r.state == IzRemitState.SUBMITTED) {
                            TextField(
                                value = undoReason,
                                onValueChange = { undoReason = it },
                                label = { Text("Undo reason") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            Spacer(Modifier.width(8.dp))
                            IzChip("Undo · 48h", onClick = { repo.undoRemit(r, undoReason); undoReason = "" })
                        }
                        if (r.state == IzRemitState.UNDONE) {
                            IzChip("Resubmit", onClick = { repo.submitRemit(r) })
                        }
                    }
                    Text(
                        "Undo returns to draft, unlocks the covered days, deletes the snapshot — reason recorded, window 48h.",
                        fontSize = 11.sp,
                        color = IzColors.Faint,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        IzSection(title = "Commission split", note = "pooled per branch day, equal shares") {
            IzCard {
                Text(
                    "One pool per branch day, split equally across on-duty practitioners. Marking paid is a local acknowledgement, not a payout rail.",
                    fontSize = 12.sp,
                    color = IzColors.Dim,
                )
                Spacer(Modifier.height(8.dp))
                repo.payouts.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.staff, fontSize = 12.sp, color = IzColors.Ink, modifier = Modifier.weight(1f))
                        IzPill(if (p.paid) "paid" else "unpaid", if (p.paid) IzTone.GREEN else IzTone.ORANGE)
                        Spacer(Modifier.width(8.dp))
                        IzChip(if (p.paid) "Reopen" else "Mark paid", onClick = { repo.togglePaid(p) })
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
