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
internal fun CabHomeScreen() {
    val repo = ArcadeCabinetFakeRepo
    val branchId = repo.clockedBranchId.value
    var dutyNote by remember { mutableStateOf("") }
    var inviteWho by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabSectionTitle("CLOCK-IN CABINET", "Home branch: ${repo.branchName(branchId)}")
        CabPanelCard(accent = if (repo.clockedIn.value) CabNeonGreen else CabChrome) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CabChip(if (repo.clockedIn.value) "CLOCKED IN" else "CLOCKED OUT", if (repo.clockedIn.value) CabNeonGreen else CabChrome)
                    CabChip("DAY ${repo.dayStatus.value}", CabNeonYellow)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Relief duty: clocking into a non-home branch starts view-only; edit access needs a relief " +
                        "grant (broadcast request approved by any branch member, or a branch relief invite). " +
                        "Expires 04:00 Manila next day; paid from the relief branch drawer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CabMuted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            repo.clockedIn.value = true
                            repo.log(repo.currentUserName.value, "CLOCK_IN", repo.branchName(branchId), "now")
                        },
                        enabled = !repo.clockedIn.value,
                        colors = ButtonDefaults.buttonColors(containerColor = CabNeonGreen, contentColor = Color.Black),
                    ) { Text("CLOCK IN", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = {
                            repo.clockedIn.value = false
                            repo.log(repo.currentUserName.value, "CLOCK_OUT", repo.branchName(branchId), "now")
                        },
                        enabled = repo.clockedIn.value,
                    ) { Text("CLOCK OUT", color = CabNeonYellow) }
                }
            }
        }
        CabPanelCard(accent = CabNeonPurple) {
            Column {
                CabSectionTitle("RELIEF ARCADE BOARD", "Requests broadcast to the whole branch - no individual named")
                Spacer(Modifier.height(8.dp))
                repo.relief.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.kind}: ${item.who}", color = CabInk, fontWeight = FontWeight.Bold)
                            Text("${item.branch} - ${item.date}", style = MaterialTheme.typography.bodySmall, color = CabMuted)
                        }
                        CabChip(item.state, if (item.state == "GRANTED") CabNeonGreen else CabNeonYellow)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                CabField(dutyNote, { dutyNote = it }, "New relief request (branch + date)")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (dutyNote.isNotBlank()) {
                                repo.relief.add(CabReliefItem("f${repo.relief.size + 1}", "REQUEST", repo.currentUserName.value, dutyNote, "today", "OPEN"))
                                repo.log(repo.currentUserName.value, "RELIEF_REQUEST", dutyNote, "now")
                                dutyNote = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CabNeonPurple, contentColor = Color.Black),
                    ) { Text("BROADCAST REQUEST", fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(8.dp))
                CabField(inviteWho, { inviteWho = it }, "Invite player to relief (name)")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (inviteWho.isNotBlank()) {
                            repo.relief.add(CabReliefItem("f${repo.relief.size + 1}", "INVITE", inviteWho, repo.branchName(branchId), "tomorrow", "OPEN"))
                            repo.log(repo.currentUserName.value, "RELIEF_INVITE", inviteWho, "now")
                            inviteWho = ""
                        }
                    },
                ) { Text("SEND RELIEF INVITE", color = CabNeonCyan) }
            }
        }
        CabPanelCard(accent = CabNeonYellow) {
            Column {
                CabSectionTitle("TODAY'S HI-SCORES", "Completed session income per branch")
                Spacer(Modifier.height(6.dp))
                repo.branches.forEachIndexed { i, b ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${i + 1}. ${b.name}", color = CabInk, fontWeight = FontWeight.Bold)
                        Text("P${repo.sessionTotal(b.id)}", color = CabNeonYellow, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CabSessionsScreen() {
    val repo = ArcadeCabinetFakeRepo
    var filter by remember { mutableStateOf<CabSessionStatus?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    val visible = repo.sessions.filter { filter == null || it.status == filter }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabSectionTitle("SESSION SELECT", "One visit, one client, one branch - PENDING flows to COMPLETED / NO_SHOW / CANCELLED")
        CabPanelCard(accent = CabNeonYellow) {
            Column {
                Text(
                    "HOUSE RULE: walk-in sessions cannot be marked NO_SHOW or CANCELLED.",
                    color = CabNeonYellow,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CabFilterPill("ALL", filter == null) { filter = null }
            CabSessionStatus.values().forEach { s ->
                CabFilterPill(s.name, filter == s) { filter = s }
            }
        }
        if (visible.isEmpty()) {
            CabEmpty("No sessions on this filter", "Insert another coin - try a different status.") {}
        }
        visible.forEach { s ->
            CabPanelCard(accent = statusColor(s.status)) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${s.id.uppercase()} - ${s.clientName}", color = CabInk, fontWeight = FontWeight.Bold)
                            Text(
                                "${s.type} - ${repo.branchName(s.branchId)} - ${s.time}${if (s.walkIn) " - WALK-IN" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CabMuted,
                            )
                        }
                        CabChip(s.status.name, statusColor(s.status))
                    }
                    if (s.voided) {
                        Spacer(Modifier.height(6.dp))
                        Text("VOIDED: ${s.voidReason}", color = CabDanger, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { expanded = if (expanded == s.id) null else s.id }) {
                            Text(if (expanded == s.id) "HIDE" else "DETAIL", color = CabNeonCyan)
                        }
                        if (!s.voided) {
                            OutlinedButton(
                                onClick = {
                                    val idx = repo.sessions.indexOfFirst { it.id == s.id }
                                    if (idx >= 0) {
                                        repo.sessions[idx] = s.copy(voided = true, voidReason = voidReason.ifBlank { "cabinet demo void" })
                                        repo.log(repo.currentUserName.value, "SESSION_VOID", "Session ${s.id}", "now", voidReason.ifBlank { "cabinet demo void" })
                                        voidReason = ""
                                    }
                                },
                            ) { Text("VOID", color = CabDanger) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val idx = repo.sessions.indexOfFirst { it.id == s.id }
                                    if (idx >= 0) {
                                        repo.sessions[idx] = s.copy(voided = false, voidReason = "")
                                        repo.log(repo.currentUserName.value, "SESSION_UNVOID", "Session ${s.id}", "now", "demo unvoid")
                                    }
                                },
                            ) { Text("UNVOID", color = CabNeonGreen) }
                        }
                    }
                    if (expanded == s.id) {
                        Spacer(Modifier.height(8.dp))
                        Text("Price: P${s.price} (defaults to base rate, overridable). Type auto-assigned from client history.", style = MaterialTheme.typography.bodySmall, color = CabMuted)
                        Spacer(Modifier.height(6.dp))
                        CabField(voidReason, { voidReason = it }, "Void reason (required for void)")
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CabSessionStatus.values().filter { it != s.status }.forEach { next ->
                                val blocked = s.walkIn && (next == CabSessionStatus.NO_SHOW || next == CabSessionStatus.CANCELLED)
                                OutlinedButton(
                                    onClick = {
                                        if (!blocked) {
                                            val idx = repo.sessions.indexOfFirst { it.id == s.id }
                                            if (idx >= 0) {
                                                repo.sessions[idx] = s.copy(status = next)
                                                repo.log(repo.currentUserName.value, "SESSION_STATUS", "Session ${s.id} -> $next", "now")
                                            }
                                        }
                                    },
                                    enabled = !blocked,
                                ) { Text(if (blocked) "$next (WALK-IN)" else "-> $next", color = if (blocked) CabMuted else CabNeonYellow) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CabFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) CabNeonCyan else Color(0xFF14141F),
            contentColor = if (selected) Color.Black else CabInk,
        ),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

private fun statusColor(s: CabSessionStatus): Color = when (s) {
    CabSessionStatus.PENDING -> CabNeonYellow
    CabSessionStatus.COMPLETED -> CabNeonGreen
    CabSessionStatus.NO_SHOW -> CabNeonPurple
    CabSessionStatus.CANCELLED -> CabDanger
}

@Composable
internal fun CabClientsScreen() {
    val repo = ArcadeCabinetFakeRepo
    var anonymizedView by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val visible = repo.clients.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabSectionTitle("PLAYER ROSTER", "Client records are global - shared across all branches")
        CabPanelCard(accent = CabNeonYellow) {
            Text(
                "LEAGUE RULE: a client holds at most one PENDING session at a time.",
                color = CabNeonYellow,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { anonymizedView = !anonymizedView }) {
                Text(if (anonymizedView) "SHOW NAMES" else "ANONYMIZED VIEW", color = CabNeonCyan)
            }
        }
        CabField(query, { query = it }, "Search roster")
        if (visible.isEmpty()) {
            CabEmpty("No players found", "Try another search - the roster is global.") {}
        }
        visible.forEach { c ->
            CabPanelCard(accent = CabNeonCyan) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (anonymizedView || c.anonymized) "PLAYER-${c.id.uppercase()}" else c.name,
                            color = CabInk,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (anonymizedView || c.anonymized) "Anonymized: PII nulled, gender+age kept for reporting" else c.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = CabMuted,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (c.hasPending) CabChip("1 PENDING", CabNeonYellow)
                        OutlinedButton(
                            onClick = {
                                val idx = repo.clients.indexOfFirst { it.id == c.id }
                                if (idx >= 0) {
                                    repo.clients[idx] = c.copy(anonymized = !c.anonymized)
                                    repo.log(repo.currentUserName.value, if (c.anonymized) "CLIENT_DEANONYMIZE" else "CLIENT_ANONYMIZE", "Client ${c.id}", "now")
                                }
                            },
                        ) { Text(if (c.anonymized) "RESTORE" else "ANONYMIZE", color = CabNeonPurple) }
                    }
                }
            }
        }
    }
}
