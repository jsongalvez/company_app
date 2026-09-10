package com.companyb.companyapp.proto.noirdetective

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun NoirFinance(repo: NoirRepo) {
    FolderTab(title = "The ledger", right = "SESSION + PRODUCT · drafts overlap freely") {}
    Row(horizontalArrangement = Arrangement.spacedBy(NoirPadMd)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NoirPadSm)) {
            CaseFolder(Modifier.fillMaxWidth()) {
                Text("SESSION DRAFT — TONIGHT", style = NoirType.titleSmall)
                Spacer(Modifier.height(4.dp))
                LedgerRow("Closed sessions", repo.closedSum())
                LedgerRow("Compensation", -repo.sessionComp)
                LedgerRow("Expenses", -repo.sessionExpense)
                VenetianBlinds()
                LedgerRow("Net to remit", repo.sessionNet(), NoirPalette.LampAmber)
                Spacer(Modifier.height(NoirPadSm))
                Button(
                    onClick = { repo.sealEnvelope("SESSION") },
                    enabled = !repo.dayLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = NoirPalette.LampAmber),
                ) {
                    Text("Seal SESSION envelope", style = NoirType.labelLarge, color = NoirPalette.NightRain)
                }
            }
            CaseFolder(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PRODUCT DRAFT", style = NoirType.titleSmall)
                    Spacer(Modifier.weight(1f))
                    NoirBadge("₱" + repo.productTotal(), NoirPalette.NeonBlue)
                }
                Spacer(Modifier.height(4.dp))
                repo.productLines.forEach { line ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(line.name, style = NoirType.bodyMedium, modifier = Modifier.weight(1f))
                        Text(line.qty.toString() + " × ₱" + line.unitPrice, style = NoirType.bodySmall)
                        OutlinedButton(onClick = { repo.removeLine(line.id) }) {
                            Text("−", style = NoirType.labelLarge, color = NoirPalette.SirenRed)
                        }
                    }
                }
                Spacer(Modifier.height(NoirPadSm))
                ProductAdder(repo)
                Spacer(Modifier.height(NoirPadSm))
                Button(
                    onClick = { repo.sealEnvelope("PRODUCT") },
                    enabled = !repo.dayLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = NoirPalette.NeonBlue),
                ) {
                    Text("Seal PRODUCT envelope", style = NoirType.labelLarge, color = NoirPalette.NightRain)
                }
            }
        }
        Column(Modifier.width(NoirDetailWidth), verticalArrangement = Arrangement.spacedBy(NoirPadSm)) {
            CaseFolder(Modifier.fillMaxWidth()) {
                Text("SEALED ENVELOPES", style = NoirType.titleSmall)
                Spacer(Modifier.height(4.dp))
                if (repo.envelopes.isEmpty()) {
                    RainyEmpty("no sealed envelopes", "seal a draft to freeze a snapshot")
                }
                repo.envelopes.forEach { envelope ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(envelope.id + " · " + envelope.kind, style = NoirType.bodyMedium)
                            Text(
                                envelope.submittedAt + " · ₱" + envelope.total,
                                style = NoirType.bodySmall,
                                color = NoirPalette.Dim,
                            )
                        }
                        CaseStamp("sealed", NoirPalette.LampAmber)
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { repo.undoTargetId = envelope.id },
                        enabled = !repo.dayLocked,
                    ) {
                        Text("Undo — reopen", style = NoirType.labelMedium, color = NoirPalette.SirenRed)
                    }
                    Spacer(Modifier.height(NoirPadSm))
                }
                DeskLampNote("Undo lives 48 hours past submission, needs a reason, and deletes the snapshot.")
            }
            CaseFolder(Modifier.fillMaxWidth()) {
                Text("THE SPLIT", style = NoirType.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Product commissions pool per branch day and split equally among every " +
                        "practitioner and coordinator clocked in at sold_at time. " +
                        "Outside remittance — tonight ₱${repo.commissionPool()} across " +
                        "${repo.clockedCrew().size} of the crew.",
                    style = NoirType.bodyMedium,
                    color = NoirPalette.Dim,
                )
            }
        }
    }
    if (repo.undoTargetId != null) UndoDialog(repo)
}

internal fun NoirRepo.closedSum(): Int =
    cases.filter { it.status == CaseStatus.COMPLETED && !it.voided }.sumOf { it.price }

internal fun NoirRepo.removeLine(id: String) {
    val index = productLines.indexOfFirst { it.id == id }
    if (index < 0) return
    productLines.removeAt(index)
    appendLog(actor(), "PRODUCT.REMOVE_LINE", "$id struck from the draft")
}

@Composable
private fun LedgerRow(
    label: String,
    amount: Int,
    color: androidx.compose.ui.graphics.Color = NoirPalette.Ink,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NoirType.bodyMedium, color = NoirPalette.Dim, modifier = Modifier.weight(1f))
        Text("₱" + amount, style = NoirType.bodyLarge, color = color)
    }
}

@Composable
private fun ProductAdder(repo: NoirRepo) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = repo.productDraftName,
            onValueChange = { repo.productDraftName = it },
            label = { Text("Line item", style = NoirType.bodySmall) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                if (repo.productDraftName.isNotBlank()) {
                    val line = LedgerLine("l-${10 + repo.productLines.size}", repo.productDraftName.trim(), 1, 300)
                    repo.productLines.add(line)
                    repo.appendLog(repo.actor(), "PRODUCT.ADD_LINE", line.name + " added to the draft")
                    repo.productDraftName = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = NoirPalette.FileRaised),
        ) {
            Text("+ add", style = NoirType.labelLarge, color = NoirPalette.LampAmber)
        }
    }
}

@Composable
private fun UndoDialog(repo: NoirRepo) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { repo.undoTargetId = null },
        title = { Text("Break the seal?", style = NoirType.titleMedium) },
        text = {
            Column {
                Text(
                    "Undo returns the remittance to draft, unlocks the covered days, " +
                        "and deletes the frozen snapshot. Only inside 48 hours, reason on the record.",
                    style = NoirType.bodyMedium,
                )
                Spacer(Modifier.height(NoirPadSm))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason", style = NoirType.bodySmall) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (reason.isNotBlank()) {
                    repo.breakSeal(repo.undoTargetId ?: return@TextButton, reason.trim())
                    repo.undoTargetId = null
                }
            }) {
                Text("Break it", style = NoirType.labelLarge, color = NoirPalette.SirenRed)
            }
        },
        dismissButton = {
            TextButton(onClick = { repo.undoTargetId = null }) {
                Text("Leave sealed", style = NoirType.labelLarge, color = NoirPalette.Dim)
            }
        },
    )
}
