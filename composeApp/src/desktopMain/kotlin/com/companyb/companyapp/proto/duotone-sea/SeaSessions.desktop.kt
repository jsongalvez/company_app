package com.companyb.companyapp.proto.duotonesea

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
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
internal fun SeaSessions(repo: SeaRepo) {
    SectionTitle("Tide chart", "sessions PENDING → COMPLETED / NO_SHOW / CANCELLED")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED", "VOIDED").forEach { filter ->
            TideChipClickable(filter, repo.swimFilter == filter, onClick = {
                repo.swimFilter = filter
                repo.bumpSwim(0)
            })
        }
    }
    Spacer(Modifier.height(SeaPadSm))
    val rows = repo.visibleSwims()
    if (rows.isEmpty()) {
        BecalmedEmpty("becalmed waters", "no swims under this filter")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { swim ->
                DriftRow(selected = swim.id == repo.selectedSwimId, onClick = { repo.selectedSwimId = swim.id }) {
                    Column(Modifier.weight(1f)) {
                        Text(swim.time + " · " + swim.clientName, style = SeaType.titleMedium)
                        Text(
                            swim.service + if (swim.walkIn) " · walk-in" else "",
                            style = SeaType.bodySmall,
                        )
                    }
                    Text("₱" + swim.price.toString(), style = SeaType.bodyMedium, color = SeaPalette.Coral)
                    Spacer(Modifier.width(SeaPadSm))
                    StatusFoam(swim.status, swim.voided)
                }
            }
        }
    }
    Spacer(Modifier.height(SeaPadMd))
    SwimDetail(repo)
    Spacer(Modifier.height(SeaPadSm))
    OutlinedButton(onClick = { repo.swimOpen = true }, enabled = !repo.dayLocked) {
        Text("+ log a swim", style = SeaType.labelMedium, color = SeaPalette.SeaGlass)
    }
    if (repo.voidTargetId != null) VoidSwimDialog(repo)
    if (repo.swimOpen) OpenSwimDialog(repo)
}

@Composable
private fun SwimDetail(repo: SeaRepo) {
    val swim = repo.swims.firstOrNull { it.id == repo.selectedSwimId }
    if (swim == null) {
        BecalmedEmpty("no swim pulled", "pick a row from the tide chart")
        return
    }
    LagoonCard {
        Text("SWIM " + swim.id.uppercase(), style = SeaType.titleSmall)
        Text(swim.clientName + " — " + swim.service, style = SeaType.titleMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusFoam(swim.status, swim.voided)
            Text("₱" + swim.price.toString(), style = SeaType.bodyLarge, color = SeaPalette.Coral)
        }
        Spacer(Modifier.height(4.dp))
        Text("Practitioner: " + swim.practitioner, style = SeaType.bodySmall, color = SeaPalette.Mist)
        if (swim.voided) {
            Spacer(Modifier.height(4.dp))
            Text("Sunk from financials: " + swim.voidReason, style = SeaType.bodySmall, color = SeaPalette.Siren)
        }
        Spacer(Modifier.height(SeaPadSm))
        val locked = repo.dayLocked || swim.voided
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { repo.setSwimStatus(swim.id, SwimStatus.COMPLETED) }, enabled = !locked) {
                    Text("Close", style = SeaType.labelMedium, color = SeaPalette.Kelp)
                }
                OutlinedButton(
                    onClick = { repo.setSwimStatus(swim.id, SwimStatus.NO_SHOW) },
                    enabled = !locked && !swim.walkIn,
                ) {
                    Text("No-show", style = SeaType.labelMedium, color = SeaPalette.SeaGlass)
                }
                OutlinedButton(
                    onClick = { repo.setSwimStatus(swim.id, SwimStatus.CANCELLED) },
                    enabled = !locked && !swim.walkIn,
                ) {
                    Text("Drop", style = SeaType.labelMedium, color = SeaPalette.Mist)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (swim.voided) {
                    OutlinedButton(onClick = { repo.unvoidSwim(swim.id) }, enabled = !repo.dayLocked) {
                        Text("Unvoid — refloat", style = SeaType.labelMedium, color = SeaPalette.Coral)
                    }
                } else {
                    OutlinedButton(onClick = { repo.voidTargetId = swim.id }, enabled = !repo.dayLocked) {
                        Text("Void with reason", style = SeaType.labelMedium, color = SeaPalette.Siren)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        NoteLine("walk-ins never take NO_SHOW or CANCELLED — they were already in the water")
    }
}

@Composable
private fun VoidSwimDialog(repo: SeaRepo) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { repo.voidTargetId = null },
        title = { Text("Sink this swim?", style = SeaType.titleMedium) },
        text = {
            Column {
                Text(
                    "Voiding excludes the session from financials but keeps the record. A reason is required.",
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
                    repo.voidSwim(repo.voidTargetId ?: return@TextButton, reason.trim())
                    repo.voidTargetId = null
                }
            }) {
                Text("Void it", style = SeaType.labelLarge, color = SeaPalette.Siren)
            }
        },
        dismissButton = {
            TextButton(onClick = { repo.voidTargetId = null }) {
                Text("Keep it", style = SeaType.labelLarge, color = SeaPalette.Mist)
            }
        },
    )
}

@Composable
private fun OpenSwimDialog(repo: SeaRepo) {
    var name by remember { mutableStateOf("") }
    var walkIn by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { repo.swimOpen = false },
        title = { Text("Log a new swim", style = SeaType.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SeaPadSm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; blocked = false },
                    label = { Text("Client name", style = SeaType.bodySmall) },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { walkIn = !walkIn }) {
                        Text(
                            if (walkIn) "walk-in ✓" else "walk-in ○",
                            style = SeaType.labelMedium,
                            color = SeaPalette.SeaGlass,
                        )
                    }
                    Spacer(Modifier.width(SeaPadSm))
                    Text("booked otherwise", style = SeaType.bodySmall, color = SeaPalette.Mist)
                }
                if (blocked) {
                    Text(
                        "That client already rides one PENDING swim — close it first.",
                        style = SeaType.bodySmall,
                        color = SeaPalette.Siren,
                    )
                }
                NoteLine("at most one PENDING session per client")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = repo.drifters.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                if (name.isBlank()) return@TextButton
                if (target == null) {
                    val freshId = "p-0" + (repo.drifters.size + 1).toString()
                    repo.drifters.add(Drifter(freshId, name.trim(), "—", 0, "—"))
                    val fresh = repo.drifters.first { it.id == freshId }
                    if (!repo.openSwim(fresh, walkIn, "Rehab 45m", 950)) blocked = true else repo.swimOpen = false
                } else {
                    if (!repo.openSwim(target, walkIn, "Rehab 45m", 950)) blocked = true else repo.swimOpen = false
                }
            }) {
                Text("Log it", style = SeaType.labelLarge, color = SeaPalette.Coral)
            }
        },
        dismissButton = {
            TextButton(onClick = { repo.swimOpen = false }) {
                Text("Not yet", style = SeaType.labelLarge, color = SeaPalette.Mist)
            }
        },
    )
}
