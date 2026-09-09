package com.companyb.companyapp.proto.reliefnetwork

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.util.logInfo

@Composable
fun RnSessions(
    repo: RnRepo,
    user: RnUser,
    branch: RnBranch,
) {
    var filter by remember { mutableStateOf("ALL") }
    var openId by remember { mutableStateOf<String?>(null) }
    var newClient by remember { mutableStateOf("") }
    var newWalkIn by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RnSectionTitle("Session slips · ${branch.name}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED").forEach { pill ->
                if (pill == filter) {
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = RnVermilion)) { Text(pill) }
                } else {
                    OutlinedButton(onClick = { filter = pill }) { Text(pill, color = RnCream) }
                }
            }
        }
        RnSlip {
            Text("Log a session", fontWeight = FontWeight.Black, color = RnInk)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newClient, onValueChange = { newClient = it }, label = { Text("Client") }, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { newWalkIn = !newWalkIn }) { Text(if (newWalkIn) "Walk-in ✓" else "Walk-in", color = RnInk) }
                Button(
                    onClick = {
                        if (newClient.isNotBlank()) {
                            repo.addSession(branch.id, newClient.trim(), newWalkIn, user.login)
                            newClient = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RnTeal),
                ) { Text("Pin it") }
            }
        }
        repo.sessions.filter { it.branchId == branch.id && (filter == "ALL" || it.status == filter) }.forEach { session ->
            RnSlip {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${session.client} · ${session.type}", fontWeight = FontWeight.Black, color = RnInk)
                        Text("₱${session.price} · ${repo.userName(session.practitioner)}${if (session.walkIn) " · walk-in" else ""}", style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
                    }
                    RnStamp(session.status, rnStatusColor(session.status), dark = true)
                    if (session.voided) {
                        Spacer(Modifier.width(6.dp))
                        RnStamp("VOID", RnDanger, dark = true)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (openId == session.id) {
                        TextButton(onClick = { openId = null }) { Text("Fold", color = RnInkSoft) }
                    } else {
                        OutlinedButton(onClick = { openId = session.id }) { Text("Open", color = RnInk) }
                    }
                    if (!session.voided && session.status == "PENDING") {
                        Button(
                            onClick = { repo.setStatus(session.id, "COMPLETED", user.login) },
                            colors = ButtonDefaults.buttonColors(containerColor = RnTeal),
                        ) { Text("Complete") }
                        if (!session.walkIn) {
                            OutlinedButton(onClick = { repo.setStatus(session.id, "NO_SHOW", user.login) }) { Text("No-show", color = RnInk) }
                            OutlinedButton(onClick = { repo.setStatus(session.id, "CANCELLED", user.login) }) { Text("Cancel", color = RnInk) }
                        }
                    }
                }
                if (session.walkIn && session.status == "PENDING" && !session.voided) {
                    Text("Walk-in rule: only COMPLETED is offered — walk-ins are never NO_SHOW or CANCELLED.", style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
                }
                if (openId == session.id) {
                    RnSessionDetail(repo, user, session) { openId = null }
                }
            }
        }
    }
}

@Composable
private fun RnSessionDetail(
    repo: RnRepo,
    user: RnUser,
    session: RnSession,
    onClose: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var askVoid by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(RnPaperDim).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Slip ${session.id} · ${session.client}", fontWeight = FontWeight.Bold, color = RnInk)
        Text("Type auto-assigned from client history. Final price defaults to the base rate, overridable.", style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
        if (session.voided) {
            Text("Voided — reason: ${session.voidReason}. Record kept, excluded from finance.", style = MaterialTheme.typography.bodySmall, color = RnDanger)
            Button(
                onClick = {
                    repo.unvoidSession(session.id, user.login)
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(containerColor = RnTeal),
            ) { Text("Unvoid — restore") }
        } else {
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Void reason (required)") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { if (reason.isNotBlank()) askVoid = true }) { Text("Void this slip", color = RnDanger) }
        }
    }
    if (askVoid) {
        AlertDialog(
            onDismissRequest = { askVoid = false },
            title = { Text("Void ${session.client}?") },
            text = { Text("The record stays pinned with a VOID stamp and leaves finance math. Reason: $reason") },
            confirmButton = {
                Button(
                    onClick = {
                        repo.voidSession(session.id, reason, user.login)
                        askVoid = false
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RnDanger),
                ) { Text("Void it") }
            },
            dismissButton = { TextButton(onClick = { askVoid = false }) { Text("Keep") } },
        )
    }
}

@Composable
fun RnClients(repo: RnRepo) {
    var anonView by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RnSectionTitle("Client ledger · global")
        RnSlip {
            Text("At most one PENDING session per client across all branches.", fontWeight = FontWeight.Bold, color = RnInk)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Anonymized counter view", color = RnInk, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { anonView = !anonView }) { Text(if (anonView) "On" else "Off", color = RnInk) }
            }
        }
        repo.clients.forEach { client ->
            RnSlip {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (anonView || client.anonymized) "Anon ${client.gender}${client.age}" else client.name, fontWeight = FontWeight.Black, color = RnInk)
                        Text("${client.gender} · age ${client.age} · ${client.pending} pending", style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
                    }
                    if (client.anonymized) RnStamp("ANON", RnViolet, dark = true)
                }
                if (!client.anonymized) {
                    OutlinedButton(onClick = { repo.anonymize(client.name) }) { Text("Anonymize (keep gender+age)", color = RnInk) }
                }
            }
        }
    }
}

@Composable
fun RnFinance(
    repo: RnRepo,
    user: RnUser,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RnSectionTitle("Money stall")
        RnSlip {
            Text("Commission split: product commissions pool per branch day and split equally among all practitioners and coordinators clocked in at sold_at time. Separate from compensation, outside remittance.", color = RnInk, style = MaterialTheme.typography.bodySmall)
        }
        repo.remittances.forEach { remit ->
            RnSlip {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${remit.kind} · ₱${remit.total}", fontWeight = FontWeight.Black, color = RnInk)
                        val sub =
                            when {
                                remit.state == "DRAFT" -> "Draft — unconstrained, may overlap."
                                else -> remit.snapshot
                            }
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
                    }
                    RnStamp(remit.state, if (remit.state == "DRAFT") RnMarigold else RnTeal, dark = remit.state != "DRAFT")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (remit.state == "DRAFT") {
                        Button(
                            onClick = {
                                repo.submitRemittance(remit.id, user.login)
                                logInfo("ReliefNetworkFinance", "${user.login} submitted ${remit.id}")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RnVermilion),
                        ) { Text("Submit — seal snapshot") }
                    } else if (remit.undoable) {
                        var reason by remember { mutableStateOf("") }
                        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Undo reason") }, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { if (reason.isNotBlank()) repo.undoRemittance(remit.id, reason, user.login) }) { Text("Undo ≤48h", color = RnInk) }
                    } else {
                        Text("Past the 48h undo window — snapshot permanent.", style = MaterialTheme.typography.bodySmall, color = RnDanger)
                    }
                }
            }
        }
    }
}

@Composable
fun RnTeam(
    repo: RnRepo,
    user: RnUser,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RnSectionTitle("Crew · roles are bundles")
        RnSlip {
            Text("MANAGER is a superset of Coordinator plus MANAGE_USERS and delegate powers. Accountant reads everything, edits nothing.", color = RnInk, style = MaterialTheme.typography.bodySmall)
        }
        repo.users.forEach { member ->
            RnSlip {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${member.name} · ${member.role}", fontWeight = FontWeight.Black, color = RnInk)
                        Text(if (member.caps.isEmpty()) "empty bundle — locked" else member.caps.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
                        Text("home ${repo.branchName(member.homeBranchId)} · ${if (member.active) "ACTIVE" else "INACTIVE"}", style = MaterialTheme.typography.bodySmall, color = RnInkSoft)
                    }
                    if (member.locked) RnStamp("ONBOARDING", RnMarigold, dark = true)
                }
                if (user.role == "MANAGER" && member.login != user.login) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (member.locked) {
                            Button(
                                onClick = { repo.grantRole(member.login, user.login) },
                                colors = ButtonDefaults.buttonColors(containerColor = RnTeal),
                            ) { Text("Grant Practitioner") }
                        }
                        if (member.active) {
                            OutlinedButton(onClick = { repo.setActive(member.login, false, user.login) }) { Text("Deactivate", color = RnDanger) }
                        } else {
                            OutlinedButton(onClick = { repo.setActive(member.login, true, user.login) }) { Text("Reactivate", color = RnInk) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RnMail(repo: RnRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RnSectionTitle("Mailbox")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { repo.notices.forEach { it.read = true } }) { Text("Mark all read", color = RnMarigold) }
        }
        repo.notices.forEach { notice ->
            RnSlip {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(notice.text, color = RnInk, modifier = Modifier.weight(1f))
                    RnStamp(if (notice.read) "READ" else "NEW", if (notice.read) RnMuted else RnVermilion, dark = !notice.read)
                }
                if (!notice.read) {
                    TextButton(onClick = { notice.read = true }) { Text("Mark read", color = RnInkSoft) }
                }
            }
        }
    }
}

@Composable
fun RnAudit(repo: RnRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RnSectionTitle("Audit wire · newest first")
        repo.audit.forEach { entry ->
            RnSlip { Text(entry, color = RnInk, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun RnProfile(
    repo: RnRepo,
    user: RnUser,
    branch: RnBranch,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RnSectionTitle("Your pass")
        RnSlip {
            Text(user.name, fontWeight = FontWeight.Black, color = RnInk)
            Text("${user.role} · home ${repo.branchName(user.homeBranchId)}", color = RnInkSoft, style = MaterialTheme.typography.bodySmall)
            Text(if (user.caps.isEmpty()) "Capabilities: none — locked." else "Capabilities: ${user.caps.joinToString(", ")}", color = RnInk, style = MaterialTheme.typography.bodySmall)
        }
        RnSlip {
            Text("Demo lever: flip this branch day", fontWeight = FontWeight.Bold, color = RnInk)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("OPEN", "PAST", "REMITTED").forEach { state ->
                    if (state == branch.dayState) {
                        Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = rnDayColor(state))) { Text(state) }
                    } else {
                        OutlinedButton(onClick = { branch.dayState = state }) { Text(state, color = RnInk) }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    repo.clocked[branch.id] = false
                    logInfo("ReliefNetworkProfile", "${user.login} clock-out + logout")
                    onLogout()
                },
                colors = ButtonDefaults.buttonColors(containerColor = RnTeal),
            ) { Text("Clock out + log out") }
            OutlinedButton(onClick = onLogout) { Text("Log out", color = RnCream) }
            TextButton(onClick = onExit) { Text("Exit stall", color = RnInkSoft) }
        }
    }
}
