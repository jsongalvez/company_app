package com.companyb.companyapp.proto.a11ymax

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun MaxHome() {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MaxSectionTitle("Home — clock in and relief")
        val clocked = A11yMaxFakeRepo.clockedIn.value
        MaxNote(
            "Signed in as ${A11yMaxFakeRepo.currentUserName.value}, Practitioner. " +
                (if (clocked) "Clocked in at ${A11yMaxFakeRepo.branchName(A11yMaxFakeRepo.clockedBranchId.value)}." else "Not clocked in yet."),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!clocked) {
                MaxBigButton("Clock in", "Clock in at the selected Branch.") {
                    A11yMaxFakeRepo.clockedIn.value = true
                    A11yMaxFakeRepo.audit(A11yMaxFakeRepo.currentUserName.value, "CLOCK_IN", A11yMaxFakeRepo.branchName(A11yMaxFakeRepo.clockedBranchId.value))
                    A11yMaxFakeRepo.say("Clocked in at ${A11yMaxFakeRepo.branchName(A11yMaxFakeRepo.clockedBranchId.value)}. Focus stayed on this section.")
                }
            } else {
                MaxBigButton("Clock out", "Clock out of the current Branch.") {
                    A11yMaxFakeRepo.clockedIn.value = false
                    A11yMaxFakeRepo.audit(A11yMaxFakeRepo.currentUserName.value, "CLOCK_OUT", A11yMaxFakeRepo.branchName(A11yMaxFakeRepo.clockedBranchId.value))
                    A11yMaxFakeRepo.say("Clocked out. Focus stayed on this section.")
                }
            }
        }
        MaxSectionTitle("Relief duty, requests, invites")
        MaxNote(
            "Relief duty starts view-only at a non-home Branch; edit access needs a grant. " +
                "Requests are outsider-initiated broadcasts; invites are Branch-initiated offers. " +
                "Every grant expires at 04:00 Manila the next day.",
        )
        A11yMaxFakeRepo.relief.forEach { item ->
            Row(
                Modifier.fillMaxWidth()
                    .border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = "${item.kind}: ${item.who}", color = A11yInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(text = "${item.branch}, ${item.date}. State: ${item.state}.", color = A11yInkSoft, style = MaterialTheme.typography.bodyMedium)
                }
                val index = A11yMaxFakeRepo.relief.indexOf(item)
                Button(
                    onClick = {
                        val next = if (item.state == "EDIT GRANTED") "VIEW ONLY" else "EDIT GRANTED"
                        A11yMaxFakeRepo.relief[index] = item.copy(state = next)
                        A11yMaxFakeRepo.audit(A11yMaxFakeRepo.currentUserName.value, "RELIEF_GRANT", "${item.who} ${item.branch}")
                        A11yMaxFakeRepo.say("${item.kind} for ${item.who} is now $next.")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = A11yAction, contentColor = A11yOnAction),
                    modifier = Modifier.height(52.dp).semantics { contentDescription = "Toggle grant for ${item.who} at ${item.branch}." },
                ) {
                    Text(if (item.state == "EDIT GRANTED") "Revoke grant" else "Grant edit", fontWeight = FontWeight.Bold)
                }
            }
        }
        var requestTarget by remember { mutableStateOf("") }
        OutlinedTextField(
            value = requestTarget,
            onValueChange = { requestTarget = it },
            label = { Text("Branch name for a new relief request") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        MaxOutlineButton("Broadcast relief request", "Broadcast a relief request for the named Branch.") {
            val target = requestTarget.ifBlank { "Harbor Provincial Tour" }
            A11yMaxFakeRepo.relief.add(MaxRelief("f${A11yMaxFakeRepo.relief.size + 1}", "Relief request", A11yMaxFakeRepo.currentUserName.value, target, "today", "AWAITING GRANT"))
            A11yMaxFakeRepo.audit(A11yMaxFakeRepo.currentUserName.value, "RELIEF_REQUEST", target)
            A11yMaxFakeRepo.say("Relief request broadcast to $target.")
            requestTarget = ""
        }
        MaxOutlineButton("Send relief invite to Lena Cruz", "Invite Lena Cruz for Saturday relief.") {
            A11yMaxFakeRepo.relief.add(MaxRelief("f${A11yMaxFakeRepo.relief.size + 1}", "Relief invite", "Lena Cruz", "Sunrise Clinic", "Saturday", "INVITED"))
            A11yMaxFakeRepo.audit(A11yMaxFakeRepo.currentUserName.value, "RELIEF_INVITE", "Lena Cruz Saturday")
            A11yMaxFakeRepo.say("Relief invite sent to Lena Cruz for Saturday.")
        }
    }
}

@Composable
internal fun MaxSessions() {
    var filter by remember { mutableStateOf<MaxSessionStatus?>(null) }
    var voidTarget by remember { mutableStateOf<MaxSession?>(null) }
    var voidReason by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MaxSectionTitle("Sessions")
        MaxNote("Walk-in sessions cannot be marked NO_SHOW or CANCELLED. The buttons are removed for walk-ins, not just disabled, so keyboard users never land on a dead control.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaxFilterPill("All", filter == null, "Show all sessions.") { filter = null }
            MaxSessionStatus.entries.forEach { status ->
                MaxFilterPill(status.name.replace('_', ' '), filter == status, "Filter sessions by ${status.name.replace('_', ' ')}.") { filter = status }
            }
        }
        MaxOutlineButton("Create walk-in session", "Create a new walk-in session, status PENDING.") {
            A11yMaxFakeRepo.addWalkIn()
        }
        val visible = A11yMaxFakeRepo.sessions.filter { filter == null || it.status == filter }
        if (visible.isEmpty()) {
            MaxNote("No sessions match this filter. Change the filter above; focus stays put.")
        }
        visible.forEach { session ->
            Column(
                Modifier.fillMaxWidth()
                    .border(3.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(16.dp)
                    .semantics { contentDescription = "Session for ${session.clientName}, ${session.status.name.replace('_', ' ')}, ${if (session.voided) "voided" else "not voided"}." },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "${session.clientName} — ${session.time}", color = A11yInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Status ${session.status.name.replace('_', ' ')}${if (session.walkIn) ", WALK-IN" else ""}. ${session.type}, ${session.price} pesos at ${A11yMaxFakeRepo.branchName(session.branchId)}.${if (session.voided) " VOIDED: ${session.voidReason}." else ""}",
                    color = A11yInkSoft,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (session.status != MaxSessionStatus.COMPLETED) {
                        MaxSmallAction("Complete", "Mark session for ${session.clientName} COMPLETED.") {
                            A11yMaxFakeRepo.setSessionStatus(session.id, MaxSessionStatus.COMPLETED)
                        }
                    }
                    if (!session.walkIn && session.status == MaxSessionStatus.PENDING) {
                        MaxSmallAction("No-show", "Mark session for ${session.clientName} NO SHOW.") {
                            A11yMaxFakeRepo.setSessionStatus(session.id, MaxSessionStatus.NO_SHOW)
                        }
                        MaxSmallAction("Cancel", "Mark session for ${session.clientName} CANCELLED.") {
                            A11yMaxFakeRepo.setSessionStatus(session.id, MaxSessionStatus.CANCELLED)
                        }
                    }
                    if (session.status != MaxSessionStatus.PENDING) {
                        MaxSmallAction("Reopen", "Reopen session for ${session.clientName} to PENDING.") {
                            A11yMaxFakeRepo.setSessionStatus(session.id, MaxSessionStatus.PENDING)
                        }
                    }
                    if (!session.voided) {
                        MaxSmallAction("Void", "Void session for ${session.clientName}. A reason is required.") {
                            voidTarget = session
                            voidReason = ""
                        }
                    } else {
                        MaxSmallAction("Unvoid", "Remove the void from session for ${session.clientName}.") {
                            A11yMaxFakeRepo.unvoidSession(session.id)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    val target = voidTarget
    if (target != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                voidTarget = null
                A11yMaxFakeRepo.say("Void dialog closed. Session list kept its place.")
            },
            title = { Text("Void session for ${target.clientName}", color = A11yInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A reason is required and lands in the audit log.", color = A11yInkSoft)
                    OutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        label = { Text("Void reason") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (voidReason.isBlank()) {
                            A11yMaxFakeRepo.say("A reason is required before voiding. Type one first.")
                        } else {
                            A11yMaxFakeRepo.voidSession(target.id, voidReason.trim())
                            voidTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = A11yAction, contentColor = A11yOnAction),
                ) { Text("Void with reason", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                MaxOutlineButton("Cancel", "Cancel voiding.") {
                    voidTarget = null
                    A11yMaxFakeRepo.say("Void dialog closed. Session list kept its place.")
                }
            },
            containerColor = A11yPaper,
        )
    }
}

@Composable
private fun MaxFilterPill(label: String, selected: Boolean, description: String, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.height(52.dp).semantics { contentDescription = "$description Currently selected." },
            colors = ButtonDefaults.buttonColors(containerColor = A11yAction, contentColor = A11yOnAction),
        ) { Text("● $label", fontWeight = FontWeight.Black) }
    } else {
        MaxSmallAction(label, description, onClick)
    }
}

@Composable
private fun MaxSmallAction(label: String, description: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(52.dp).semantics { contentDescription = description },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = A11yOnDark),
        border = androidx.compose.foundation.BorderStroke(2.dp, A11yOnDark),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun MaxClients() {
    var anonymizedView by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MaxSectionTitle("Clients — one global record each")
        MaxNote("A Client is global across all Branches and holds at most one PENDING session at a time. Anonymizing is a soft-delete with PII nullification; gender and age stay for reporting.")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search clients by name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MaxOutlineButton(
                if (anonymizedView) "Show full view" else "Show anonymized view",
                "Toggle between full and anonymized client views.",
            ) {
                anonymizedView = !anonymizedView
                A11yMaxFakeRepo.say(if (anonymizedView) "Anonymized view on. Names hidden." else "Full view on.")
            }
        }
        val visible = A11yMaxFakeRepo.clients.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        if (visible.isEmpty()) {
            MaxNote("No clients match. Clear the search; focus stays put.")
        }
        visible.forEach { client ->
            val index = A11yMaxFakeRepo.clients.indexOf(client)
            val shownName = if (anonymizedView || client.anonymized) "Anonymized client ${client.id.uppercase()}" else client.name
            Column(
                Modifier.fillMaxWidth()
                    .border(3.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(16.dp)
                    .semantics { contentDescription = "Client $shownName. ${if (client.hasPending) "Has one PENDING session." else "No PENDING session."}" },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = shownName, color = A11yInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (anonymizedView || client.anonymized) "Gender and age retained for reporting. PII nullified." else client.detail,
                    color = A11yInkSoft,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (client.hasPending) "PENDING session: yes (at most one allowed)." else "PENDING session: none.",
                    color = A11yInkSoft,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                MaxSmallAction(
                    if (client.anonymized) "Restore record" else "Anonymize record",
                    if (client.anonymized) "Restore record for ${client.name}." else "Anonymize record for ${client.name}, keeping gender and age.",
                ) {
                    A11yMaxFakeRepo.clients[index] = client.copy(anonymized = !client.anonymized)
                    A11yMaxFakeRepo.audit(A11yMaxFakeRepo.currentUserName.value, if (client.anonymized) "CLIENT_RESTORE" else "CLIENT_ANONYMIZE", client.id)
                    A11yMaxFakeRepo.say(if (client.anonymized) "Client record restored." else "Client anonymized. Gender and age retained.")
                }
            }
        }
    }
}
