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
internal fun MaxFinance() {
    var undoTarget by remember { mutableStateOf<MaxRemittance?>(null) }
    var undoReason by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MaxSectionTitle("Finance and remittance")
        MaxNote(
            "Two independent flows. SESSION submits net income after compensation and expenses; " +
                "PRODUCT submits unit price times quantity. Submission freezes an immutable snapshot. " +
                "Undo returns the remittance to Draft and deletes the snapshot, only within 48 hours, with a reason.",
        )
        MaxNote(
            "Commission split note: product commissions pool per Branch day and split equally among all " +
                "Practitioners and Coordinators clocked in at the sold-at time. Manual inclusions or exclusions " +
                "can override. Separate from compensation and not subject to remittance.",
        )
        MaxRemitKind.entries.forEach { kind ->
            MaxSectionTitle("${kind.name} remittances")
            val drafts = A11yMaxFakeRepo.remittances.filter { it.kind == kind }
            if (drafts.isEmpty()) {
                MaxNote("No ${kind.name} remittances. Drafts are unconstrained and may overlap.")
            }
            drafts.forEach { remit ->
                val stateWord = if (remit.status == MaxRemitStatus.SUBMITTED) "SUBMITTED" else "DRAFT"
                Column(
                    Modifier.fillMaxWidth()
                        .border(3.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                        .semantics { contentDescription = "${kind.name} remittance, $stateWord, ${remit.amount} pesos, ${remit.dayLabel}." },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${kind.name} — ${remit.amount} pesos — $stateWord",
                        color = A11yInk,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (remit.status == MaxRemitStatus.SUBMITTED) "Snapshot: ${remit.snapshot}. Undo window: ${remit.submittedAt}." else "Draft for ${remit.dayLabel}. Submit seals the snapshot.",
                        color = A11yInkSoft,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (remit.status == MaxRemitStatus.DRAFT) {
                            Button(
                                onClick = { A11yMaxFakeRepo.submitRemittance(remit.id) },
                                modifier = Modifier.height(52.dp).semantics { contentDescription = "Submit ${kind.name} remittance of ${remit.amount} pesos." },
                                colors = ButtonDefaults.buttonColors(containerColor = A11yAction, contentColor = A11yOnAction),
                            ) { Text("Submit", fontWeight = FontWeight.Bold) }
                        } else {
                            Button(
                                onClick = {
                                    undoTarget = remit
                                    undoReason = ""
                                },
                                modifier = Modifier.height(52.dp).semantics { contentDescription = "Undo ${kind.name} remittance within 48 hours. A reason is required." },
                                colors = ButtonDefaults.buttonColors(containerColor = A11yBad, contentColor = A11yBlack),
                            ) { Text("Undo within 48h", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    val target = undoTarget
    if (target != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                undoTarget = null
                A11yMaxFakeRepo.say("Undo dialog closed. Finance list kept its place.")
            },
            title = { Text("Undo ${target.kind} remittance", color = A11yInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The remittance returns to Draft, covered days unlock, the snapshot is deleted. A reason is required.", color = A11yInkSoft)
                    OutlinedTextField(
                        value = undoReason,
                        onValueChange = { undoReason = it },
                        label = { Text("Undo reason") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (undoReason.isBlank()) {
                            A11yMaxFakeRepo.say("A reason is required before undo. Type one first.")
                        } else {
                            A11yMaxFakeRepo.undoRemittance(target.id, undoReason.trim())
                            undoTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = A11yBad, contentColor = A11yBlack),
                ) { Text("Undo with reason", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                MaxOutlineButton("Cancel", "Cancel undo.") {
                    undoTarget = null
                    A11yMaxFakeRepo.say("Undo dialog closed. Finance list kept its place.")
                }
            },
            containerColor = A11yPaper,
        )
    }
}

@Composable
internal fun MaxTeam() {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MaxSectionTitle("Team — users and roles")
        MaxNote("Roles bundle capabilities. Practitioner logs sessions; Coordinator owns finance and PAST or REMITTED edits; MANAGER adds user management; Accountant reads everything and edits nothing.")
        A11yMaxFakeRepo.users.forEach { user ->
            val index = A11yMaxFakeRepo.users.indexOf(user)
            Column(
                Modifier.fillMaxWidth()
                    .border(3.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(16.dp)
                    .semantics { contentDescription = "User ${user.name}, role ${user.role}, home ${A11yMaxFakeRepo.branchName(user.homeBranchId)}." },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "${user.name} — ${user.role}", color = A11yInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Home Branch ${A11yMaxFakeRepo.branchName(user.homeBranchId)}." +
                        if (user.onboarding) " ONBOARDING: locked, empty capability bundle." else " Capability bundle: ${roleBlurb(user.role)}.",
                    color = A11yInkSoft,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (user.onboarding) {
                    Button(
                        onClick = {
                            A11yMaxFakeRepo.users[index] = user.copy(role = "Practitioner", onboarding = false)
                            A11yMaxFakeRepo.audit(A11yMaxFakeRepo.currentUserName.value, "GRANT_ROLE", "${user.name} Practitioner")
                            A11yMaxFakeRepo.say("${user.name} granted the Practitioner role. Account unlocked.")
                        },
                        modifier = Modifier.height(52.dp).semantics { contentDescription = "Grant ${user.name} the Practitioner role." },
                        colors = ButtonDefaults.buttonColors(containerColor = A11yAction, contentColor = A11yOnAction),
                    ) { Text("Grant Practitioner role", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun roleBlurb(role: String): String = when (role) {
    "Practitioner" -> "sessions, inventory, clients"
    "Coordinator" -> "finance, remittance, PAST-day edits"
    "MANAGER" -> "coordinator plus user management"
    "Accountant" -> "read-only, all Branches"
    else -> role
}

@Composable
internal fun MaxMailbox() {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MaxSectionTitle("Mailbox — ${A11yMaxFakeRepo.unreadCount} unread")
        MaxNote("Every message is stored per recipient with read or unread state. Read rows stay forever as history. Relief events name the Branch and the day.")
        MaxOutlineButton("Mark all read", "Mark every mailbox message read.") {
            A11yMaxFakeRepo.markAllRead()
        }
        if (A11yMaxFakeRepo.mailbox.isEmpty()) {
            MaxNote("Mailbox empty. Nothing to read.")
        }
        A11yMaxFakeRepo.mailbox.forEach { mail ->
            Column(
                Modifier.fillMaxWidth()
                    .border(if (mail.read) 2.dp else 4.dp, if (mail.read) MaterialTheme.colorScheme.onBackground else A11yFocus, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(16.dp)
                    .semantics { contentDescription = "Message ${mail.title}. ${if (mail.read) "Read." else "Unread."} ${mail.body}" },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${if (mail.read) "READ" else "UNREAD"}: ${mail.title}",
                    color = A11yInk,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(text = "${mail.body} Day: ${mail.day}.", color = A11yInkSoft, style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { A11yMaxFakeRepo.toggleRead(mail.id) },
                    modifier = Modifier.height(52.dp).semantics {
                        contentDescription = if (mail.read) "Mark message ${mail.title} unread." else "Mark message ${mail.title} read."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = A11yOnDark),
                    border = androidx.compose.foundation.BorderStroke(2.dp, A11yOnDark),
                    // Note: dark-on-dark container keeps the mailbox on the black canvas; the white border holds contrast.
                ) { Text(if (mail.read) "Mark unread" else "Mark read", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
internal fun MaxAuditLog() {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MaxSectionTitle("Audit log — newest first")
        MaxNote("Immutable record of every mutation: who changed what, when, and why. Void, unvoid, submit, undo, relief, and clock events all land here.")
        if (A11yMaxFakeRepo.audits.isEmpty()) {
            MaxNote("No audit entries yet.")
        }
        A11yMaxFakeRepo.audits.forEach { entry ->
            Column(
                Modifier.fillMaxWidth()
                    .border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(16.dp)
                    .semantics { contentDescription = "Audit ${entry.action} by ${entry.actor} on ${entry.record} at ${entry.whenText}." },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "${entry.action} — ${entry.record}", color = A11yInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(text = "By ${entry.actor}, ${entry.whenText}.${if (entry.reason.isNotBlank()) " Reason: ${entry.reason}." else ""}", color = A11yInkSoft, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun MaxProfile(onLogout: () -> Unit, onExit: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MaxSectionTitle("Profile — ${A11yMaxFakeRepo.currentUserName.value}")
        MaxNote("Practitioner, home Branch ${A11yMaxFakeRepo.branchName("b1")}. Text size applies instantly across every section. Branch-day flips rewrite the top banner for demo purposes.")
        MaxSectionTitle("Text size")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            A11yTextScale.entries.forEach { scale ->
                val selected = A11yMaxFakeRepo.textScale.value == scale
                Button(
                    onClick = {
                        A11yMaxFakeRepo.textScale.value = scale
                        A11yMaxFakeRepo.say("${scale.label} on. Every section resized.")
                    },
                    modifier = Modifier.height(52.dp).semantics { contentDescription = "${scale.label}.${if (selected) " Currently selected." else ""}" },
                    colors = if (selected) {
                        ButtonDefaults.buttonColors(containerColor = A11yAction, contentColor = A11yOnAction)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = A11yOnDark)
                    },
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (selected) A11yOnAction else A11yOnDark),
                ) { Text(scale.label, fontWeight = FontWeight.Bold) }
            }
        }
        MaxSectionTitle("Branch day (demo flip)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaxDayStatus.entries.forEach { status ->
                val selected = A11yMaxFakeRepo.dayStatus.value == status
                Button(
                    onClick = {
                        A11yMaxFakeRepo.dayStatus.value = status
                        A11yMaxFakeRepo.audit(A11yMaxFakeRepo.currentUserName.value, "DAY_STATUS_FLIP", status.name)
                        A11yMaxFakeRepo.say("Branch day is now ${status.name}. Banner rewritten.")
                    },
                    modifier = Modifier.height(52.dp).semantics { contentDescription = "Set Branch day to ${status.name}.${if (selected) " Currently selected." else ""}" },
                    colors = if (selected) {
                        ButtonDefaults.buttonColors(containerColor = A11yAction, contentColor = A11yOnAction)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A), contentColor = A11yOnDark)
                    },
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (selected) A11yOnAction else A11yOnDark),
                ) { Text(status.name, fontWeight = FontWeight.Bold) }
            }
        }
        MaxSectionTitle("Session controls")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MaxBigButton("Clock out", "Clock out of the current Branch.") {
                A11yMaxFakeRepo.clockedIn.value = false
                A11yMaxFakeRepo.audit(A11yMaxFakeRepo.currentUserName.value, "CLOCK_OUT", A11yMaxFakeRepo.branchName(A11yMaxFakeRepo.clockedBranchId.value))
                A11yMaxFakeRepo.say("Clocked out.")
            }
            MaxOutlineButton("Log out", "Log out and return to sign in.") {
                A11yMaxFakeRepo.say("Logged out. Back at sign in.")
                onLogout()
            }
        }
        MaxOutlineButton("Reset demo data", "Reset all fake demo data.") {
            A11yMaxFakeRepo.reset()
        }
        MaxOutlineButton("Exit prototype", "Exit the prototype window.") { onExit() }
    }
}
