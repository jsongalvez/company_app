package com.companyb.companyapp.proto.lunchrush

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LunchRushFinance(repo: LunchRushRepo) {
    RushSectionHeader("Till", "SESSION + PRODUCT drafts, submit locks a snapshot")
    if (repo.surgeActive) {
        RushFlag("SURGE ON — walk-in Express tickets bill +10% peak surcharge at submit")
        Spacer(Modifier.height(8.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            RushTicketCard(hot = true) {
                Text("SESSION DRAFT", style = LunchRushType.labelMedium)
                Text(peso(repo.sessionNet()), style = LunchRushType.displaySmall)
                Spacer(Modifier.height(4.dp))
                Text("Completed minus complimentaries (P400) and expenses (P150).", style = LunchRushType.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { repo.submitRemittance("SESSION") },
                    colors = ButtonDefaults.buttonColors(containerColor = LunchRushPalette.Leaf),
                ) { Text("SUBMIT SESSION") }
            }
        }
        Column(Modifier.weight(1f)) {
            RushTicketCard {
                Text("PRODUCT DRAFT", style = LunchRushType.labelMedium)
                Text(peso(repo.productTotal()), style = LunchRushType.displaySmall)
                Spacer(Modifier.height(4.dp))
                repo.productLines.forEach { line ->
                    RushRow(
                        left = { Text("${line.name} x${line.qty}", style = LunchRushType.bodyMedium) },
                        right = { Text(peso(line.qty * line.unitPrice), style = LunchRushType.labelLarge) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = repo.productDraftName,
                    onValueChange = { repo.productDraftName = it },
                    label = { Text("Item") },
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    OutlinedTextField(
                        value = repo.productDraftQty,
                        onValueChange = { repo.productDraftQty = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Qty") },
                        singleLine = true,
                        modifier = Modifier.width(110.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val qty = repo.productDraftQty.toIntOrNull() ?: 1
                            repo.addProductLine(repo.productDraftName.ifBlank { "Counter Item" }, qty.coerceAtLeast(1), 150)
                        },
                    ) { Text("Add line") }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { repo.submitRemittance("PRODUCT") },
                    colors = ButtonDefaults.buttonColors(containerColor = LunchRushPalette.Turmeric),
                ) { Text("SUBMIT PRODUCT") }
            }
        }
    }
    Spacer(Modifier.height(RushPadMd))
    RushTicketCard {
        Text("SNAPSHOTS", style = LunchRushType.labelMedium)
        if (repo.snapshots.isEmpty()) Text("No snapshots yet — submit a draft to lock one.", style = LunchRushType.bodyMedium)
        repo.snapshots.forEach { snap ->
            RushRow(
                left = {
                    Column {
                        Text("${snap.id} — ${snap.kind}", style = LunchRushType.titleMedium)
                        Text("Filed ${snap.submittedAt} — ${peso(snap.total)}", style = LunchRushType.bodySmall)
                    }
                },
                right = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RushBadge(if (snap.undoable) "UNDO 48H" else "FINAL", if (snap.undoable) LunchRushPalette.Turmeric else LunchRushPalette.Faint)
                        if (snap.undoable) {
                            OutlinedButton(onClick = { repo.undoTargetId = snap.id }) { Text("Undo") }
                        }
                    }
                },
            )
        }
    }
    Spacer(Modifier.height(RushPadMd))
    RushNote(
        "Undo rule: a snapshot reopens only within 48h of submit and only with a written reason. " +
            "Commission split note: a 5% pool splits equally across clocked-in crew, paid outside remittance — submitting the till never moves commission money.",
    )
    if (repo.undoTargetId != null) {
        AlertDialog(
            onDismissRequest = { repo.undoTargetId = null },
            title = { Text("Reopen ${repo.undoTargetId}?") },
            text = {
                Column {
                    Text("Undo window: 48h after submit. Reason is required.", style = LunchRushType.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repo.undoReason,
                        onValueChange = { repo.undoReason = it },
                        label = { Text("Reason") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = repo.undoTargetId
                        if (target != null) repo.undoRemittance(target, repo.undoReason.ifBlank { "miscount" })
                        repo.undoTargetId = null
                        repo.undoReason = ""
                    },
                ) { Text("Reopen") }
            },
            dismissButton = { TextButton(onClick = { repo.undoTargetId = null }) { Text("Keep locked") } },
        )
    }
}
