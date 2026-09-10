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

private val CaseFilters = listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED", "VOIDED")

@Composable
internal fun NoirSessions(repo: NoirRepo) {
    FolderTab(
        title = "Case files",
        right = repo.visibleCases().size.toString() + " folders on the board",
    ) {
        FilterStrip(repo)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(NoirPadMd)) {
        Column(Modifier.weight(1f)) {
            CaseFolder(Modifier.fillMaxWidth()) {
                CaseRows(repo)
            }
            Spacer(Modifier.height(NoirPadSm))
            DeskLampNote("Walk-in sessions cannot be marked NO_SHOW or CANCELLED — the client is already here.")
        }
        Column(Modifier.width(NoirDetailWidth)) {
            CaseFolder(Modifier.fillMaxWidth()) {
                CaseDetail(repo)
            }
        }
    }
    Spacer(Modifier.height(NoirPadSm))
    Row(horizontalArrangement = Arrangement.spacedBy(NoirPadSm)) {
        Button(
            onClick = { repo.fileOpen = true },
            enabled = !repo.dayLocked,
            colors = ButtonDefaults.buttonColors(containerColor = NoirPalette.LampAmber),
        ) {
            Text("+ open a case", style = NoirType.labelLarge, color = NoirPalette.NightRain)
        }
        if (repo.dayLocked) NoirBadge("day covered — coordinator edit only", NoirPalette.LampAmber)
    }
    if (repo.voidTargetId != null) VoidDialog(repo)
    if (repo.fileOpen) OpenCaseDialog(repo)
}

@Composable
private fun FilterStrip(repo: NoirRepo) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CaseFilters.forEach { filter ->
            NoirBadgeClickable(filter, repo.caseFilter == filter) { repo.caseFilter = filter }
        }
    }
}

@Composable
private fun CaseRows(repo: NoirRepo) {
    val rows = repo.visibleCases()
    if (rows.isEmpty()) {
        RainyEmpty("no folders under " + repo.caseFilter, "loosen the filter")
        return
    }
    rows.forEach { file ->
        FileRow(selected = file.id == repo.selectedCaseId, onClick = { repo.selectedCaseId = file.id }) {
            Text(
                if (file.id == repo.selectedCaseId) "▸" else "·",
                style = NoirType.bodyMedium,
                color = NoirPalette.LampAmber,
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(file.time, style = NoirType.bodyMedium, color = NoirPalette.NeonBlue)
                    Text(file.clientName, style = NoirType.bodyMedium)
                    if (file.walkIn) NoirBadge("WALK-IN", NoirPalette.NeonBlue)
                }
                Text(
                    file.id.uppercase() + " · " + file.service + " · " + file.practitioner,
                    style = NoirType.bodySmall,
                    color = NoirPalette.Dim,
                )
            }
            StatusInk(file.status, file.voided)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CaseDetail(repo: NoirRepo) {
    val file = repo.cases.firstOrNull { it.id == repo.selectedCaseId }
    if (file == null) {
        RainyEmpty("no folder pulled", "pick a case from the board")
        return
    }
    Text("FOLDER " + file.id.uppercase(), style = NoirType.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text(file.clientName + " — " + file.service, style = NoirType.titleMedium)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusInk(file.status, file.voided)
        Text("₱" + file.price.toString(), style = NoirType.bodyLarge, color = NoirPalette.LampAmber)
    }
    Spacer(Modifier.height(4.dp))
    Text("Practitioner: " + file.practitioner, style = NoirType.bodySmall, color = NoirPalette.Dim)
    if (file.voided) {
        Spacer(Modifier.height(4.dp))
        Text("Struck from the ledger: " + file.voidReason, style = NoirType.bodySmall, color = NoirPalette.SirenRed)
    }
    Spacer(Modifier.height(NoirPadSm))
    val locked = repo.dayLocked || file.voided
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { repo.setCaseStatus(file.id, CaseStatus.COMPLETED) }, enabled = !locked) {
                Text("Close", style = NoirType.labelMedium, color = NoirPalette.EvidenceGreen)
            }
            OutlinedButton(
                onClick = { repo.setCaseStatus(file.id, CaseStatus.NO_SHOW) },
                enabled = !locked && !file.walkIn,
            ) {
                Text("No-show", style = NoirType.labelMedium, color = NoirPalette.NeonBlue)
            }
            OutlinedButton(
                onClick = { repo.setCaseStatus(file.id, CaseStatus.CANCELLED) },
                enabled = !locked && !file.walkIn,
            ) {
                Text("Drop", style = NoirType.labelMedium, color = NoirPalette.Dim)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (file.voided) {
                OutlinedButton(onClick = { repo.unvoidCase(file.id) }, enabled = !repo.dayLocked) {
                    Text("Unvoid — restore", style = NoirType.labelMedium, color = NoirPalette.LampAmber)
                }
            } else {
                OutlinedButton(onClick = { repo.voidTargetId = file.id }, enabled = !repo.dayLocked) {
                    Text("Void with reason", style = NoirType.labelMedium, color = NoirPalette.SirenRed)
                }
            }
        }
    }
}

@Composable
private fun VoidDialog(repo: NoirRepo) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { repo.voidTargetId = null },
        title = { Text("Strike it from the ledger?", style = NoirType.titleMedium) },
        text = {
            Column {
                Text(
                    "Voiding excludes the session from financials but keeps the record. A reason is required.",
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
                    repo.voidCase(repo.voidTargetId ?: return@TextButton, reason.trim())
                    repo.voidTargetId = null
                }
            }) {
                Text("Void it", style = NoirType.labelLarge, color = NoirPalette.SirenRed)
            }
        },
        dismissButton = {
            TextButton(onClick = { repo.voidTargetId = null }) {
                Text("Keep it", style = NoirType.labelLarge, color = NoirPalette.Dim)
            }
        },
    )
}

@Composable
private fun OpenCaseDialog(repo: NoirRepo) {
    var name by remember { mutableStateOf("") }
    var walkIn by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { repo.fileOpen = false },
        title = { Text("Open a new case", style = NoirType.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NoirPadSm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Client name", style = NoirType.bodySmall) },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { walkIn = !walkIn }) {
                        Text(
                            if (walkIn) "☑ walk-in" else "☐ walk-in",
                            style = NoirType.labelLarge,
                            color = NoirPalette.NeonBlue,
                        )
                    }
                }
                Text(
                    "A client holds at most one PENDING session — the desk refuses a second.",
                    style = NoirType.bodySmall,
                    color = NoirPalette.Dim,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val known = repo.persons.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                    val person = known ?: Person("p-x", name.trim(), "M", 30, "—")
                    val alreadyPending = repo.pendingPersonIds().contains(person.id)
                    if (!alreadyPending) {
                        repo.openCase(person, walkIn, "Rehab 45m", 950)
                        repo.fileOpen = false
                    }
                }
            }) {
                Text("File it", style = NoirType.labelLarge, color = NoirPalette.LampAmber)
            }
        },
        dismissButton = {
            TextButton(onClick = { repo.fileOpen = false }) {
                Text("Shred it", style = NoirType.labelLarge, color = NoirPalette.Dim)
            }
        },
    )
}
