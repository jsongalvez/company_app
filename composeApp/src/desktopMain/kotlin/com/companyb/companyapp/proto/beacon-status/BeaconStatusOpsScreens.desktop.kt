package com.companyb.companyapp.proto.beaconstatus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun BsClientsTab() {
    val repo = BeaconStatusFakeRepo
    var query by remember { mutableStateOf("") }
    var masked by remember { mutableStateOf(false) }
    val rows = repo.clients.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    BsScroll {
        Text("CLIENTS · GLOBAL BOOK", style = MaterialTheme.typography.headlineSmall, color = FogWhite)
        Text(
            "One shared book across Branches. At most one PENDING session per Client — a second stamp is refused. " +
                "Masked view keeps gender + age for reporting.",
            color = FogDim,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search the book") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = FogWhite, unfocusedTextColor = FogWhite),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            BsChip(if (masked) "MASKED" else "OPEN", masked) { masked = !masked }
        }
        if (rows.isEmpty()) Text("No names on this page.", color = FogDim)
        rows.forEach { client ->
            val pending = repo.sessions.any { it.clientName == client.name && it.status == BsSessionStatus.PENDING }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(HarborDeep)
                        .border(1.dp, HarborEdge, MaterialTheme.shapes.medium)
                        .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (masked && client.anonymized) "······ (anonymized)" else client.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = FogWhite,
                        )
                        Text(
                            if (masked) "Anonymized view · ${client.genderAge}" else client.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = FogDim,
                        )
                    }
                    if (pending) Text("PENDING", style = MaterialTheme.typography.labelLarge, color = LampAmber)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    if (pending) return@OutlinedButton
                    val no = "S-${repo.signalSeq++}"
                    repo.sessions.add(
                        0,
                        BsSession(
                            "s-$no",
                            no,
                            client.name,
                            repo.clockedBranchId.value,
                            BsSessionStatus.PENDING,
                            "Follow-up",
                            850,
                            walkIn = false,
                            time = "now",
                        ),
                    )
                    repo.log(repo.currentUser.value.name, "LOG SIGNAL", "$no · ${client.name}")
                }) {
                    Text(
                        if (pending) "Second PENDING refused" else "Stamp PENDING",
                        color = if (pending) FogDim else BeaconGold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BsVaultTab() {
    val repo = BeaconStatusFakeRepo
    val me = repo.currentUser.value
    var adjustKind by remember { mutableStateOf(BsRemitKind.SESSION) }
    var freeAmount by remember { mutableStateOf("") }
    BsScroll {
        Text("VAULT · SESSION + PRODUCT DRAWERS", style = MaterialTheme.typography.headlineSmall, color = FogWhite)
        Text(
            "Count the drawer, seal the snapshot. Submit seals an immutable snapshot (SN-*). " +
                "Undo runs 48h and needs a reason. Commission split: lane 60 / house 40, relief splits even.",
            color = FogDim,
        )
        BsPanel("COUNT LOOSE BILLS", "Tap a drawer, bump the count, add it to the draft.") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BsRemitKind.values().forEach { kind -> BsChip(kind.name, adjustKind == kind) { adjustKind = kind } }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = freeAmount,
                onValueChange = { freeAmount = it.filter { c -> c.isDigit() } },
                label = { Text("Amount ₱") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = FogWhite, unfocusedTextColor = FogWhite),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        val amount = freeAmount.toIntOrNull() ?: return@Button
                        val draft =
                            repo.remittances.firstOrNull {
                                it.kind == adjustKind &&
                                    it.status == BsRemitStatus.DRAFT
                            }
                        if (draft == null) {
                            repo.remittances.add(
                                0,
                                BsRemittance(
                                    "r-${repo.remitSeq++}",
                                    adjustKind,
                                    BsRemitStatus.DRAFT,
                                    amount,
                                    "Today · ${repo.branchName(repo.clockedBranchId.value)}",
                                ),
                            )
                        } else {
                            repo.remittances.remove(draft)
                            repo.remittances.add(0, draft.copy(amount = draft.amount + amount))
                        }
                        repo.log(me.name, "COUNT", "${adjustKind.name} +₱$amount")
                        freeAmount = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BeaconGold, contentColor = HarborNight),
                ) {
                    Text("Add to draft")
                }
                OutlinedButton(onClick = { freeAmount = (freeAmount.toIntOrNull() ?: 0).plus(500).toString() }) {
                    Text("+500", color = SeaFoam)
                }
            }
        }
        repo.remittances.forEach { remit ->
            BsPanel(
                "${remit.kind} · ${remit.dayLabel}",
                "₱${remit.amount} · ${remit.status}" +
                    if (remit.snapshotNo.isNotEmpty()) " · ${remit.snapshotNo}" else "",
            ) {
                if (remit.status == BsRemitStatus.DRAFT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                val snap = "SN-${1042 + repo.remitSeq}"
                                repo.remittances.remove(remit)
                                repo.remittances.add(0, remit.copy(status = BsRemitStatus.SUBMITTED, snapshotNo = snap))
                                repo.log(me.name, "SEAL", snap)
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = LampGreen,
                                    contentColor = HarborNight,
                                ),
                        ) {
                            Text("Submit + seal")
                        }
                    }
                } else {
                    var undoReason by remember { mutableStateOf("") }
                    Text("Sealed — undo runs 48h and needs a reason.", color = FogDim)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = undoReason,
                        onValueChange = { undoReason = it },
                        label = { Text("Undo reason") },
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = FogWhite,
                                unfocusedTextColor = FogWhite,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        if (undoReason.isBlank()) return@OutlinedButton
                        repo.remittances.remove(remit)
                        repo.remittances.add(
                            0,
                            remit.copy(status = BsRemitStatus.DRAFT, snapshotNo = "", note = undoReason),
                        )
                        repo.log(me.name, "UNDO SEAL", remit.snapshotNo, undoReason)
                    }) { Text("Undo (48h)", color = LampAmber) }
                }
            }
        }
    }
}

@Composable
internal fun BsCrewTab() {
    val repo = BeaconStatusFakeRepo
    BsScroll {
        Text("CREW · WATCH BILL", style = MaterialTheme.typography.headlineSmall, color = FogWhite)
        Text(
            "Every role on the manifest. ONBOARDING rows stay locked until MANAGE_USERS grants a real role.",
            color = FogDim,
        )
        val counts = repo.directory.groupBy { it.role }.mapValues { it.value.size }
        BsPanel("HEADCOUNT", counts.entries.joinToString(" · ") { "${it.key} ${it.value}" }) {
            repo.directory.forEach { user ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.titleMedium, color = FogWhite)
                        Text(
                            "${user.role} · home ${repo.branchName(user.homeBranchId)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FogDim,
                        )
                    }
                    Text(
                        if (user.onboarding) "LOCKED" else "CLEARED",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (user.onboarding) LampRed else LampGreen,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BsMailTab() {
    val repo = BeaconStatusFakeRepo
    BsScroll {
        Text("MAIL · SIGNAL LOCKER", style = MaterialTheme.typography.headlineSmall, color = FogWhite)
        Text("Harbor mail — tap a letter to file it read.", color = FogDim)
        OutlinedButton(onClick = {
            repo.mailbox.replaceAll { it.copy(read = true) }
        }) { Text("File all read", color = BeaconGold) }
        repo.mailbox.forEach { mail ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (mail.read) HarborDeep else HarborCard)
                        .border(1.dp, HarborEdge, MaterialTheme.shapes.medium)
                        .clickable {
                            val idx = repo.mailbox.indexOfFirst { it.id == mail.id }
                            if (idx >= 0) repo.mailbox[idx] = mail.copy(read = true)
                        }.padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(mail.title, style = MaterialTheme.typography.titleMedium, color = FogWhite)
                        Text("${mail.day} · ${mail.body}", style = MaterialTheme.typography.bodySmall, color = FogDim)
                    }
                    Text(
                        if (mail.read) "READ" else "NEW",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (mail.read) FogDim else BeaconGold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BsLogbookTab() {
    val repo = BeaconStatusFakeRepo
    BsScroll {
        Text("LOGBOOK · AUDIT TRAIL", style = MaterialTheme.typography.headlineSmall, color = FogWhite)
        Text("Every prototype mutation, newest first — actor, action, record, reason.", color = FogDim)
        if (repo.audit.isEmpty()) Text("Blank pages.", color = FogDim)
        repo.audit.forEach { entry ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(HarborDeep)
                        .border(1.dp, HarborEdge, MaterialTheme.shapes.medium)
                        .padding(12.dp),
            ) {
                Text(
                    "${entry.action} · ${entry.record}",
                    style = MaterialTheme.typography.titleMedium,
                    color = FogWhite,
                )
                Text(
                    "${entry.actor} · ${entry.whenText}" + if (entry.reason.isNotEmpty()) " · ${entry.reason}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = FogDim,
                )
            }
        }
    }
}

@Composable
internal fun BsProfileTab(
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    val repo = BeaconStatusFakeRepo
    val me = repo.currentUser.value
    val clocked by repo.clockedIn
    BsScroll {
        Text("PROFILE · LOCKER", style = MaterialTheme.typography.headlineSmall, color = FogWhite)
        BsPanel(me.name, "${me.role} · home ${repo.branchName(me.homeBranchId)}") {
            Text(if (clocked) "On watch — clock out before you leave the harbor." else "Off watch.", color = FogDim)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    repo.clockedIn.value = false
                    repo.log(me.name, "CLOCK OUT", repo.branchName(repo.clockedBranchId.value))
                }) { Text("Clock out", color = LampAmber) }
                OutlinedButton(onClick = onLogout) { Text("Log out", color = BeaconGold) }
                OutlinedButton(onClick = onExit) { Text("Close", color = FogDim) }
            }
        }
    }
}
