package com.companyb.companyapp.proto.arcadecabinet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun CabFinanceScreen() {
    val repo = ArcadeCabinetFakeRepo
    var undoReason by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabSectionTitle("BONUS STAGE: REMITTANCE", "SESSION (net income) and PRODUCT (price x qty) run as independent flows")
        CabPanelCard(accent = CabNeonYellow) {
            Column {
                Text(
                    "At submission an immutable snapshot freezes the P&L. Commission split: practitioner compensation " +
                        "is settled from the branch drawer before net income is reported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CabMuted,
                )
                Spacer(Modifier.height(6.dp))
                Text("SESSION income P${repo.sessionTotal(repo.clockedBranchId.value)} - PRODUCT income P${repo.productTotal()}", color = CabNeonYellow, fontWeight = FontWeight.Bold)
            }
        }
        CabPanelCard(accent = CabNeonCyan) {
            Column {
                CabSectionTitle("PRODUCT COUNTER", "Draft lines are unconstrained and can overlap")
                Spacer(Modifier.height(6.dp))
                repo.products.forEach { p ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${p.name} x${p.qty}", color = CabInk, fontWeight = FontWeight.Bold)
                        Text("P${p.price * p.qty}", color = CabNeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                CabField(productName, { productName = it }, "New product line (name)")
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        if (productName.isNotBlank()) {
                            repo.products.add(CabProductLine("p${repo.products.size + 1}", productName, 250, 1))
                            productName = ""
                        }
                    },
                ) { Text("ADD LINE P250 x1", color = CabNeonCyan) }
            }
        }
        repo.remittances.forEach { r ->
            CabPanelCard(accent = if (r.status == CabRemitStatus.SUBMITTED) CabNeonGreen else CabChrome) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${r.kind} - ${r.id.uppercase()}", color = CabInk, fontWeight = FontWeight.Bold)
                        CabChip(r.status.name, if (r.status == CabRemitStatus.SUBMITTED) CabNeonGreen else CabChrome)
                    }
                    Text(r.dayLabel, style = MaterialTheme.typography.bodySmall, color = CabMuted)
                    Text("Amount P${r.amount}", color = CabNeonYellow, fontWeight = FontWeight.Bold)
                    if (r.snapshot.isNotEmpty()) Text("SNAPSHOT: ${r.snapshot}", style = MaterialTheme.typography.bodySmall, color = CabNeonCyan)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.status == CabRemitStatus.DRAFT) {
                            Button(
                                onClick = {
                                    val idx = repo.remittances.indexOfFirst { it.id == r.id }
                                    if (idx >= 0) {
                                        val amount = if (r.kind == CabRemitKind.SESSION) repo.sessionTotal(repo.clockedBranchId.value) else repo.productTotal()
                                        repo.remittances[idx] = r.copy(status = CabRemitStatus.SUBMITTED, amount = amount, snapshot = "net=$amount sealed", submittedAt = "now")
                                        repo.log(repo.currentUserName.value, "REMIT_SUBMIT", "${r.kind} remittance ${r.id}", "now")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CabNeonGreen, contentColor = Color.Black),
                            ) { Text("SUBMIT + SEAL", fontWeight = FontWeight.Bold) }
                        } else if (r.undoable) {
                            OutlinedButton(
                                onClick = {
                                    val idx = repo.remittances.indexOfFirst { it.id == r.id }
                                    if (idx >= 0) {
                                        repo.remittances[idx] = r.copy(status = CabRemitStatus.DRAFT, snapshot = "", undoable = false)
                                        repo.log(repo.currentUserName.value, "REMIT_UNDO", "Remittance ${r.id}", "now", undoReason.ifBlank { "demo undo inside 48h" })
                                        undoReason = ""
                                    }
                                },
                            ) { Text("UNDO (48H)", color = CabNeonYellow) }
                        } else {
                            Text("Snapshot permanent - 48h window closed.", style = MaterialTheme.typography.bodySmall, color = CabMuted)
                        }
                    }
                    if (r.status == CabRemitStatus.SUBMITTED && r.undoable) {
                        Spacer(Modifier.height(6.dp))
                        CabField(undoReason, { undoReason = it }, "Undo reason (recorded in audit)")
                    }
                }
            }
        }
        CabPanelCard(accent = CabNeonPink) {
            Button(
                onClick = {
                    repo.remittances.add(CabRemittance("r${repo.remittances.size + 1}", CabRemitKind.SESSION, CabRemitStatus.DRAFT, 0, "${repo.branchName(repo.clockedBranchId.value)} - today"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = CabNeonPink, contentColor = Color.Black),
            ) { Text("NEW SESSION DRAFT", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
internal fun CabTeamScreen() {
    val repo = ArcadeCabinetFakeRepo
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabSectionTitle("CHARACTER SELECT", "Users, roles, and capability bundles")
        repo.users.forEach { u ->
            CabPanelCard(accent = if (u.onboarding) CabDanger else CabNeonPurple) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(u.name.uppercase(), color = CabInk, fontWeight = FontWeight.Bold)
                        Text(
                            "${u.role} - home ${repo.branchName(u.homeBranchId)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CabMuted,
                        )
                        if (u.onboarding) Text("LOCKED: empty capability bundle", color = CabDanger, fontWeight = FontWeight.Bold)
                    }
                    CabChip(u.role, if (u.onboarding) CabDanger else CabNeonPurple)
                }
            }
        }
        CabPanelCard(accent = CabNeonYellow) {
            Column {
                Text("ROLE GLANCE", color = CabNeonYellow, fontWeight = FontWeight.Bold)
                Text("Practitioner: sessions + clients. Coordinator: finance + PAST/REMITTED edits. MANAGER: superset + users + delegates. Accountant: read-only all branches. ONBOARDING: locked.", style = MaterialTheme.typography.bodySmall, color = CabMuted)
            }
        }
    }
}

@Composable
internal fun CabMailScreen() {
    val repo = ArcadeCabinetFakeRepo
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabSectionTitle("MESSAGE BOARD", "Relief items name branch and day")
        OutlinedButton(
            onClick = {
                repo.notes.forEachIndexed { i, n -> repo.notes[i] = n.copy(read = true) }
            },
        ) { Text("MARK ALL READ", color = CabNeonCyan) }
        val unread = repo.notes.count { !it.read }
        if (unread == 0) {
            CabEmpty("Board cleared", "All messages read. New game!") {}
        }
        repo.notes.forEach { n ->
            CabPanelCard(accent = if (n.read) CabChrome else CabNeonPink) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(n.title, color = CabInk, fontWeight = FontWeight.Bold)
                        Text(n.body, style = MaterialTheme.typography.bodySmall, color = CabMuted)
                        Text(n.day, style = MaterialTheme.typography.bodySmall, color = CabNeonCyan)
                    }
                    OutlinedButton(
                        onClick = {
                            val idx = repo.notes.indexOfFirst { it.id == n.id }
                            if (idx >= 0) repo.notes[idx] = n.copy(read = !n.read)
                        },
                    ) { Text(if (n.read) "UNREAD" else "READ", color = CabNeonYellow) }
                }
            }
        }
    }
}

@Composable
internal fun CabAuditScreen() {
    val repo = ArcadeCabinetFakeRepo
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabSectionTitle("REPLAY LOG", "Void / unvoid / submit / undo land here with reasons")
        if (repo.audits.isEmpty()) {
            CabEmpty("No replays yet", "Play a flow - every move is recorded.") {}
        }
        repo.audits.forEach { a ->
            CabPanelCard(accent = CabChrome) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(a.action, color = CabNeonCyan, fontWeight = FontWeight.Bold)
                        Text(a.whenText, style = MaterialTheme.typography.bodySmall, color = CabMuted)
                    }
                    Text("${a.actor} - ${a.record}", style = MaterialTheme.typography.bodySmall, color = CabInk)
                    if (a.reason.isNotEmpty()) Text("Reason: ${a.reason}", style = MaterialTheme.typography.bodySmall, color = CabNeonYellow)
                }
            }
        }
    }
}

@Composable
internal fun CabProfileScreen(onLogout: () -> Unit) {
    val repo = ArcadeCabinetFakeRepo
    var name by remember { mutableStateOf(repo.currentUserName.value) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabSectionTitle("PLAYER CARD", "Profile, day-state flip, logout, clock-out")
        CabPanelCard(accent = CabNeonPink) {
            Column {
                CabField(name, { name = it }, "Player name")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { repo.currentUserName.value = name.ifBlank { "Player 1" } }) {
                    Text("SAVE NAME", color = CabNeonCyan)
                }
            }
        }
        CabPanelCard(accent = CabNeonYellow) {
            Column {
                Text("DAY STATE: ${repo.dayStatus.value}", color = CabNeonYellow, fontWeight = FontWeight.Bold)
                Text("OPEN stays editable to 04:00 Manila next morning, then lazily PAST. REMITTED needs Coordinator edits with flagged audits.", style = MaterialTheme.typography.bodySmall, color = CabMuted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CabDayStatus.values().forEach { d ->
                        OutlinedButton(onClick = { repo.dayStatus.value = d }) {
                            Text(d.name, color = if (repo.dayStatus.value == d) CabNeonYellow else CabMuted)
                        }
                    }
                }
            }
        }
        CabPanelCard(accent = CabDanger) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            repo.clockedIn.value = false
                            repo.log(repo.currentUserName.value, "CLOCK_OUT", repo.branchName(repo.clockedBranchId.value), "now")
                            onLogout()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CabDanger, contentColor = Color.Black),
                    ) { Text("CLOCK OUT + EJECT", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = {
                            repo.reset()
                            onLogout()
                        },
                    ) { Text("RESET DEMO", color = CabNeonYellow) }
                }
            }
        }
    }
}
