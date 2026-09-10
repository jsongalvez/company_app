package com.companyb.companyapp.proto.duotonesea

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
internal fun SeaFinance(repo: SeaRepo) {
    SectionTitle("Harbor ledger", "remittance SESSION + PRODUCT · snapshot · undo within 48h")
    SessionDraftCard(repo)
    Spacer(Modifier.height(SeaPadMd))
    ProductDraftCard(repo)
    Spacer(Modifier.height(SeaPadMd))
    SealedCatches(repo)
    Spacer(Modifier.height(SeaPadMd))
    CommissionNote(repo)
    if (repo.undoTargetId != null) UndoCatchDialog(repo)
}

@Composable
private fun SessionDraftCard(repo: SeaRepo) {
    val closed = repo.swims.filter { it.status == SwimStatus.COMPLETED && !it.voided }.sumOf { it.price }
    LagoonCard {
        Text("SESSION DRAFT · " + repo.currentDay.date, style = SeaType.titleSmall)
        Text("net = closed − comp − expenses", style = SeaType.bodySmall, color = SeaPalette.Mist)
        Spacer(Modifier.height(4.dp))
        Text("Closed: ₱$closed", style = SeaType.bodyMedium)
        Text("Comp: ₱" + repo.sessionComp.toString(), style = SeaType.bodyMedium)
        Text("Expenses: ₱" + repo.sessionExpense.toString(), style = SeaType.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text("Net: ₱" + repo.sessionNet().toString(), style = SeaType.titleLarge, color = SeaPalette.Coral)
        Spacer(Modifier.height(SeaPadSm))
        Button(
            onClick = { repo.sealCatch("SESSION") },
            enabled = !repo.dayLocked,
            colors = ButtonDefaults.buttonColors(containerColor = SeaPalette.Coral),
        ) {
            Text("SEAL SESSION", style = SeaType.labelLarge, color = SeaPalette.Abyss)
        }
        if (repo.dayLocked) {
            Spacer(Modifier.height(4.dp))
            NoteLine("day is " + repo.currentDay.state.label + " — sealing locked for " + repo.currentUser.role)
        }
    }
}

@Composable
private fun ProductDraftCard(repo: SeaRepo) {
    var name by remember { mutableStateOf("") }
    LagoonCard {
        Text("PRODUCT DRAFT", style = SeaType.titleSmall)
        Spacer(Modifier.height(4.dp))
        if (repo.productLines.isEmpty()) {
            Text("cargo hold empty — add a line below", style = SeaType.bodySmall, color = SeaPalette.Mist)
        } else {
            repo.productLines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        line.name + " ×" + line.qty.toString(),
                        style = SeaType.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("₱" + (line.qty * line.unitPrice).toString(), style = SeaType.bodyMedium)
                    Spacer(Modifier.width(SeaPadSm))
                    OutlinedButton(onClick = { repo.productLines.remove(line) }) {
                        Text("−", style = SeaType.labelMedium, color = SeaPalette.Siren)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Total: ₱" + repo.productTotal().toString(), style = SeaType.titleMedium, color = SeaPalette.SeaGlass)
        Spacer(Modifier.height(SeaPadSm))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Item", style = SeaType.bodySmall) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                if (name.isNotBlank()) {
                    repo.productLines.add(CargoLine("l-" + (repo.productLines.size + 1).toString(), name.trim(), 1, 250))
                    repo.productDraftName = ""
                    name = ""
                }
            }) {
                Text("+ add", style = SeaType.labelMedium, color = SeaPalette.SeaGlass)
            }
        }
        Spacer(Modifier.height(SeaPadSm))
        Button(
            onClick = { repo.sealCatch("PRODUCT") },
            enabled = !repo.dayLocked && repo.productLines.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = SeaPalette.SeaGlass),
        ) {
            Text("SEAL PRODUCT", style = SeaType.labelLarge, color = SeaPalette.Abyss)
        }
    }
}

@Composable
private fun SealedCatches(repo: SeaRepo) {
    LagoonCard {
        Text("SEALED CATCHES · snapshots", style = SeaType.titleSmall)
        Spacer(Modifier.height(4.dp))
        if (repo.catches.isEmpty()) {
            Text("nothing sealed yet", style = SeaType.bodySmall, color = SeaPalette.Mist)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repo.catches.forEach { sealed ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(sealed.id + " · " + sealed.kind, style = SeaType.titleMedium)
                            Text(sealed.submittedAt, style = SeaType.bodySmall)
                        }
                        Text("₱" + sealed.total.toString(), style = SeaType.bodyLarge, color = SeaPalette.Coral)
                        Spacer(Modifier.width(SeaPadSm))
                        OutlinedButton(
                            onClick = { repo.undoTargetId = sealed.id },
                            enabled = sealed.undoable && !repo.dayLocked,
                        ) {
                            Text("Undo", style = SeaType.labelMedium, color = SeaPalette.SunBuoy)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        NoteLine("undo reopens the envelope within 48h of submit, with a reason")
    }
}

@Composable
private fun CommissionNote(repo: SeaRepo) {
    val pool = repo.commissionPool()
    val sailors = repo.clockedCrew()
    val each = if (sailors.isEmpty()) 0 else pool / sailors.size
    LagoonCard {
        Text("COMMISSION SPLIT · outside remittance", style = SeaType.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text("5% pool of session net: ₱$pool", style = SeaType.bodyMedium)
        Text(
            "Split equally across " + sailors.size.toString() + " clocked-in crew: ₱$each each",
            style = SeaType.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        sailors.forEach { sailor ->
            Text("≈ " + sailor.name + " · " + sailor.role, style = SeaType.bodySmall, color = SeaPalette.Mist)
        }
        Spacer(Modifier.height(4.dp))
        NoteLine("commission never lands inside the sealed envelope")
    }
}

@Composable
private fun UndoCatchDialog(repo: SeaRepo) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { repo.undoTargetId = null },
        title = { Text("Reopen " + (repo.undoTargetId ?: "") + "?", style = SeaType.titleMedium) },
        text = {
            Column {
                Text(
                    "Undo restores the draft and voids the snapshot. A reason is required; the 48h window applies.",
                    style = SeaType.bodyMedium,
                )
                Spacer(Modifier.height(SeaPadSm))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason", style = SeaType.bodySmall) },
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
                Text("Reopen", style = SeaType.labelLarge, color = SeaPalette.SunBuoy)
            }
        },
        dismissButton = {
            TextButton(onClick = { repo.undoTargetId = null }) {
                Text("Keep sealed", style = SeaType.labelLarge, color = SeaPalette.Mist)
            }
        },
    )
}
