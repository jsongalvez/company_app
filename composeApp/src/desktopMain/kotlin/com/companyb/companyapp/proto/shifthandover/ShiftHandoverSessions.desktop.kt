package com.companyb.companyapp.proto.shifthandover

import androidx.compose.foundation.layout.Arrangement
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

// #771 — sessions (status machine, walk-in rule, void with reason) and clients
// (global record, one-PENDING rule, anonymized view). Fake data only.

@Composable
fun ShSessionsScreen(repo: ShiftRepo) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var newClient by remember { mutableStateOf("") }
    var voidReason by remember { mutableStateOf("") }
    val branchSessions = repo.sessions.filter { it.branchId == repo.currentBranchId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShHeadline("Sessions · ${repo.currentBranch()?.name ?: "—"}")
        ShNote("PENDING → COMPLETED / NO_SHOW / CANCELLED. Walk-in sessions cannot be NO_SHOW or CANCELLED.")
        branchSessions.forEach { s ->
            ShRowCard(onClick = { selectedId = if (selectedId == s.id) null else s.id }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ShDot(s.status.seal())
                        Spacer(Modifier.width(8.dp))
                        Text("${s.time} · ${s.clientName}", color = ShColors.Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        ShStatusChip(
                            (if (s.voided) "VOIDED · " else "") + s.status.name,
                            if (s.voided) ShColors.Danger else s.status.seal(),
                        )
                    }
                    Text(
                        "${s.kind}${if (s.walkIn) " · walk-in" else ""} · ₱${s.price} · ${s.practitioners}",
                        color = ShColors.Faded,
                        fontSize = 12.sp,
                    )
                    if (selectedId == s.id) {
                        if (s.status == ShSessionStatus.PENDING && !s.voided) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShPrimary("Complete") {
                                    s.status = ShSessionStatus.COMPLETED
                                    repo.log("COMPLETE ${s.id} ${s.clientName}")
                                }
                                if (!s.walkIn) {
                                    ShGhost("No-show") {
                                        s.status = ShSessionStatus.NO_SHOW
                                        repo.log("NO_SHOW ${s.id} ${s.clientName}")
                                    }
                                    ShGhost("Cancel") {
                                        s.status = ShSessionStatus.CANCELLED
                                        repo.log("CANCEL ${s.id} ${s.clientName}")
                                    }
                                } else {
                                    ShNote("Walk-in: NO_SHOW / CANCELLED disabled by rule.")
                                }
                            }
                        }
                        if (!s.voided) {
                            TextField(
                                value = voidReason,
                                onValueChange = { voidReason = it },
                                label = { Text("Void reason (required)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            ShGhost("Void session") {
                                if (voidReason.isNotBlank()) {
                                    s.voided = true
                                    s.voidReason = voidReason
                                    repo.log("VOID ${s.id} reason: $voidReason")
                                    voidReason = ""
                                }
                            }
                        } else {
                            ShNote("Voided: ${s.voidReason}")
                            ShGhost("Unvoid") {
                                s.voided = false
                                repo.log("UNVOID ${s.id} (was: ${s.voidReason})")
                            }
                        }
                    }
                }
            }
        }
        ShCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Book session (fake)", color = ShColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                ShNote("New bookings land PENDING. One client holds at most one PENDING session at a time.")
                TextField(
                    value = newClient,
                    onValueChange = { newClient = it },
                    label = { Text("Client name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ShPrimary("Book PENDING") {
                    if (newClient.isNotBlank()) {
                        val n = repo.sessions.size + 1
                        repo.sessions.add(
                            ShSession("s-%02d".format(n), "16:30", newClient, repo.currentBranchId, "Follow-up", false, ShSessionStatus.PENDING, 1200, repo.currentUser?.name ?: "—"),
                        )
                        repo.log("CREATE session for $newClient (PENDING)")
                        newClient = ""
                    }
                }
            }
        }
    }
}

@Composable
fun ShClientsScreen(repo: ShiftRepo) {
    var query by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShHeadline("Clients · global record")
        ShNote("One global record shared across all branches. At most one PENDING session per client. Anonymized rows keep gender + age for reporting.")
        TextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search clients") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        repo.clients.filter { it.name.contains(query, ignoreCase = true) }.forEach { c ->
            ShRowCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = ShColors.Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (c.anonymized) "Anonymized view · gender ${c.gender} · age ${c.age} · PII nulled" else "Contact ${c.contact}",
                            color = ShColors.Faded,
                            fontSize = 12.sp,
                        )
                    }
                    if (c.pendingCount > 0) ShStatusChip("1 PENDING", ShColors.Tape)
                }
            }
        }
        ShCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Register client (fake)", color = ShColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Full name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ShPrimary("Register") {
                    if (newName.isNotBlank()) {
                        repo.clients.add(ShClient("c-%02d".format(repo.clients.size + 1), newName, "09xx-xxx-xxxx", 0))
                        repo.log("REGISTER client $newName")
                        newName = ""
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
