package com.companyb.companyapp.proto.daybreakroutine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DaybreakFinance(repo: DaybreakRepo) {
    var lineName by remember { mutableStateOf("Herbal oil") }
    var lineQty by remember { mutableStateOf("2") }
    var lineUnit by remember { mutableStateOf("350") }
    Column(Modifier.fillMaxWidth()) {
        DawnSectionHeader("Finance", "SESSION + PRODUCT · remittance") {
            DawnBadge("net " + dawnPeso(repo.sessionNet()), DaybreakPalette.Ink)
        }
        DawnPanel(Modifier.fillMaxWidth()) {
            Text("SESSION draft", style = DaybreakType.titleMedium)
            Text("Net = completed minus comp minus expenses.", style = DaybreakType.bodySmall)
            DawnRow(
                "Completed",
                dawnPeso(
                    repo.sessions
                        .filter {
                            it.status == DawnSessionStatus.COMPLETED && !it.voided
                        }.sumOf { it.price },
                ),
            )
            DawnRow("Compensation", dawnPeso(repo.sessionComp))
            DawnRow("Expenses", dawnPeso(repo.sessionExpense))
            DawnRow("Net", dawnPeso(repo.sessionNet()))
            Row {
                OutlinedButton(onClick = { repo.submitRemittance("SESSION") }) { Text("Submit SESSION") }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Snapshot freezes P&L at submit.",
                    style = DaybreakType.bodySmall,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        DawnPanel(Modifier.fillMaxWidth()) {
            Text("PRODUCT draft", style = DaybreakType.titleMedium)
            repo.productLines.forEach { line ->
                DawnRow(line.name + " x${line.qty}", dawnPeso(line.qty * line.unitPrice))
            }
            DawnRow("Total", dawnPeso(repo.productTotal()))
            OutlinedTextField(value = lineName, onValueChange = {
                lineName = it
            }, label = { Text("Line name") }, modifier = Modifier.fillMaxWidth())
            Row {
                OutlinedTextField(value = lineQty, onValueChange = {
                    lineQty = it
                }, label = { Text("Qty") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value = lineUnit, onValueChange = {
                    lineUnit = it
                }, label = { Text("Unit") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row {
                OutlinedButton(
                    onClick = {
                        repo.addProductLine(
                            lineName.ifBlank { "Supply" },
                            lineQty.toIntOrNull() ?: 1,
                            lineUnit.toIntOrNull() ?: 100,
                        )
                    },
                ) { Text("Add line") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { repo.submitRemittance("PRODUCT") }) { Text("Submit PRODUCT") }
            }
        }
        Spacer(Modifier.height(8.dp))
        DawnPanel(Modifier.fillMaxWidth()) {
            Text("Snapshots", style = DaybreakType.titleMedium)
            if (repo.snapshots.isEmpty()) Text("No snapshots yet.", style = DaybreakType.bodySmall)
            repo.snapshots.forEach { snap ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            snap.id + " · " + snap.kind + " · " + dawnPeso(snap.total),
                            style = DaybreakType.bodyMedium,
                        )
                        Text(
                            snap.clock + (if (snap.withinUndo) " · Undo-48h open" else " · permanent"),
                            style = DaybreakType.bodySmall,
                        )
                    }
                    if (snap.withinUndo) {
                        OutlinedButton(onClick = { repo.undoTargetId = snap.id }) { Text("Undo") }
                    } else {
                        DawnBadge("LOCKED", DaybreakPalette.Violet)
                    }
                }
            }
            DawnNote(
                "Undo returns the remittance to Draft, unlocks covered days, deletes the snapshot. " +
                    "Needs a reason, inside 48h only.",
            )
        }
        DawnNote(
            "Commission split: 5% pool (${dawnPeso(
                repo.commissionPool(),
            )}) shared equally across clocked-in crew, outside remittance.",
        )
        val undoTarget = repo.snapshots.firstOrNull { it.id == repo.undoTargetId }
        if (undoTarget != null) {
            AlertDialog(
                onDismissRequest = { repo.undoTargetId = null },
                title = { Text("Undo ${undoTarget.id} with reason") },
                text = {
                    OutlinedTextField(
                        value = repo.undoReason,
                        onValueChange = { repo.undoReason = it },
                        label = { Text("Reason") },
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            repo.undoRemittance(undoTarget.id, repo.undoReason.ifBlank { "miscount found" })
                            repo.undoTargetId = null
                            repo.undoReason = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DaybreakPalette.Rose),
                    ) { Text("Undo") }
                },
                dismissButton = { TextButton(onClick = { repo.undoTargetId = null }) { Text("Cancel") } },
            )
        }
    }
}
