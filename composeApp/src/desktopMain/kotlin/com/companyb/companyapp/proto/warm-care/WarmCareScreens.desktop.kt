package com.companyb.companyapp.proto.warmcare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun WarmHome() {
    val repo = WarmCareFakeRepo
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WarmSectionTitle(
            "Good morning, ${repo.currentUserName.value}",
            "Sunrise Clinic is humming - two neighbors are waiting with tea.",
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WarmCard(modifier = Modifier.weight(1f)) {
                Column {
                    Text(
                        text = if (repo.clockedIn.value) "You are clocked in" else "Ready when you are",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (repo.clockedIn.value) {
                            "Home branch: ${repo.branchName(repo.clockedBranchId.value)}. Have a gentle shift."
                        } else {
                            "Clock in to your home branch to start caring today."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            repo.clockedIn.value = !repo.clockedIn.value
                        },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(if (repo.clockedIn.value) "Clock out" else "Clock in at home branch")
                    }
                }
            }
            WarmCard(modifier = Modifier.weight(1f)) {
                Column {
                    Text(text = "Relief duty", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Cover a sister branch for a day. View-only until a branch member grants edit access.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        OutlinedButton(
                            onClick = {
                                repo.relief.add(
                                    WarmReliefItem(
                                        "w${repo.relief.size + 1}", "Request",
                                        repo.currentUserName.value, "Lingap Medical Mission",
                                        "Jun 16", "Waiting for a branch member",
                                    ),
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("Ask for relief")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                repo.relief.add(
                                    WarmReliefItem(
                                        "w${repo.relief.size + 1}", "Invite", "Lena Cruz",
                                        repo.branchName(repo.clockedBranchId.value),
                                        "Jun 17", "Offered - awaiting answer",
                                    ),
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("Invite help")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        WarmCard {
            Column {
                Text(text = "Relief board - requests and invites", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (repo.relief.isEmpty()) {
                    WarmEmpty(
                        "No relief plans yet",
                        "Quiet week - invite a friend or offer a neighboring branch a hand.",
                    )
                } else {
                    repo.relief.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.kind}: ${item.who} - ${item.branch}",
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${item.date} - ${item.state}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            WarmChip(item.state, soft = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WarmSessions() {
    val repo = WarmCareFakeRepo
    var filter by remember { mutableStateOf("All") }
    var selectedId by remember { mutableStateOf(repo.sessions.firstOrNull()?.id ?: "") }
    var reason by remember { mutableStateOf("") }
    val visible = repo.sessions.filter { filter == "All" || it.status.name == filter }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WarmSectionTitle("Today's sessions", "PENDING flows to COMPLETED, NO_SHOW, or CANCELLED. Walk-ins stay gentle.")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "House rule: walk-in sessions cannot be marked NO_SHOW or CANCELLED - " +
                "neighbors who drop in are always welcome.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED").forEach { name ->
                val on = filter == name
                if (on) {
                    Button(onClick = { filter = name }, shape = RoundedCornerShape(16.dp)) { Text(name) }
                } else {
                    OutlinedButton(onClick = { filter = name }, shape = RoundedCornerShape(16.dp)) { Text(name) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (visible.isEmpty()) {
            WarmEmpty("All clear, no $filter sessions", "A calm list is a gift - enjoy a slow cup of tea.")
            return
        }
        visible.forEach { session ->
            WarmCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${session.time} - ${session.clientName}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "${session.type} - Php ${session.price} - ${repo.branchName(session.branchId)}" +
                                    if (session.walkIn) " - walk-in" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        WarmChip(session.status.name)
                        if (session.voided) {
                            Spacer(Modifier.width(8.dp))
                            WarmChip("VOIDED")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedId = session.id }) { Text("Details") }
                        if (session.status == WarmSessionStatus.PENDING && !session.voided) {
                            TextButton(onClick = { repo.completeSession(session.id) }) { Text("Complete") }
                        }
                    }
                    if (selectedId == session.id) {
                        Spacer(Modifier.height(8.dp))
                        if (session.voided) {
                            Text(text = "Voided: ${session.voidReason}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { repo.unvoidSession(session.id) },
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text("Unvoid - entered in error")
                            }
                        } else {
                            OutlinedTextField(
                                value = reason,
                                onValueChange = { reason = it },
                                label = { Text("Void reason (required)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { repo.voidSession(session.id, reason.ifBlank { "Duplicate entry" }) },
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text("Void with reason")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun WarmClients() {
    val repo = WarmCareFakeRepo
    var showAnonymized by remember { mutableStateOf(true) }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WarmSectionTitle("Neighbors across every branch", "Clients are global - one record follows them everywhere.")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Kindness guardrail: a client holds at most one PENDING session at a time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WarmChip(if (showAnonymized) "Showing anonymized rows" else "Hiding anonymized rows", soft = true)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { showAnonymized = !showAnonymized }) {
                Text(if (showAnonymized) "Hide" else "Show")
            }
        }
        Spacer(Modifier.height(12.dp))
        repo.clients.filter { showAnonymized || !it.anonymized }.forEach { client ->
            WarmCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = client.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = client.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (client.hasPending) WarmChip("1 PENDING")
                    if (client.anonymized) {
                        Spacer(Modifier.width(8.dp))
                        WarmChip("Anonymized", soft = true)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun WarmFinance() {
    val repo = WarmCareFakeRepo
    var undoReason by remember { mutableStateOf("") }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WarmSectionTitle("Finance with a human face", "Drafts are free and overlapping - submission seals a snapshot.")
        Spacer(Modifier.height(12.dp))
        listOf(WarmRemitKind.SESSION, WarmRemitKind.PRODUCT).forEach { kind ->
            Text(
                text = if (kind == WarmRemitKind.SESSION) {
                    "SESSION - net income after compensation and expenses"
                } else {
                    "PRODUCT - unit price x quantity"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            val items = repo.remittances.filter { it.kind == kind }
            if (items.isEmpty()) {
                WarmEmpty("No ${kind.name} drafts", "Start a draft whenever the drawer feels ready.")
            } else {
                items.forEach { remit ->
                    WarmCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${remit.dayLabel} - Php ${remit.amount}",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    if (remit.snapshot.isNotEmpty()) {
                                        Text(
                                            text = remit.snapshot,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                WarmChip(remit.status.name)
                            }
                            Spacer(Modifier.height(10.dp))
                            if (remit.status == WarmRemitStatus.DRAFT) {
                                Button(
                                    onClick = { repo.submitRemittance(remit.id) },
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text("Submit and seal snapshot")
                                }
                            } else {
                                OutlinedTextField(
                                    value = undoReason,
                                    onValueChange = { undoReason = it },
                                    label = { Text("Undo reason (required)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        repo.undoRemittance(
                                            remit.id,
                                            undoReason.ifBlank { "Drawer miscount" },
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text("Undo within 48h")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        WarmCard {
            Column {
                Text(text = "Commission split, shared fairly", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Product commissions pool per branch day and split equally among every " +
                        "practitioner and coordinator clocked in at sold_at time. Manual inclusions can adjust.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun WarmTeam() {
    val repo = WarmCareFakeRepo
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WarmSectionTitle("The care team", "Practitioners heal, coordinators keep the books, MANAGERs open doors.")
        Spacer(Modifier.height(12.dp))
        repo.users.forEach { user ->
            WarmCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = user.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${user.role} - home: ${repo.branchName(user.homeBranchId)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    WarmChip(user.role, soft = user.onboarding)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        WarmCard {
            Column {
                Text(text = "Roles at a glance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Practitioner: sessions and clients. Coordinator: finance and remittance. " +
                        "MANAGER: coordinator plus users and delegates. Accountant: read-only everywhere. " +
                        "ONBOARDING: locked until a real role is granted.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun WarmMailbox() {
    val repo = WarmCareFakeRepo
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                WarmSectionTitle("Mailbox", "Relief news names its branch and day - tap through to that branch day.")
            }
            TextButton(onClick = { repo.markAllNotesRead() }) { Text("Mark all read") }
        }
        Spacer(Modifier.height(12.dp))
        val unread = repo.notes.count { !it.read }
        if (repo.notes.isEmpty()) {
            WarmEmpty("Mailbox bliss", "No letters today - the whole team is caught up. Enjoy the quiet.")
            return
        }
        Text(
            text = "$unread unread - read rows stay forever as history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        repo.notes.forEach { note ->
            WarmCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = (if (note.read) "" else "New - ") + note.title,
                            fontWeight = if (note.read) FontWeight.Normal else FontWeight.Bold,
                        )
                        Text(text = note.body, style = MaterialTheme.typography.bodyMedium)
                        if (note.day.isNotEmpty()) {
                            Text(
                                text = "About: ${note.day}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { repo.toggleNoteRead(note.id) }) {
                        Text(if (note.read) "Unread" else "Read")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun WarmAuditList() {
    val repo = WarmCareFakeRepo
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WarmSectionTitle("Audit log", "Every change keeps its who, what, when, and why - forever.")
        Spacer(Modifier.height(12.dp))
        if (repo.audits.isEmpty()) {
            WarmEmpty("Nothing changed yet", "Mutations will appear here with their reasons attached.")
            return
        }
        repo.audits.forEach { entry ->
            WarmCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = "${entry.action} - ${entry.record}", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${entry.actor} - ${entry.whenText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.reason.isNotEmpty()) {
                        Text(text = "Why: ${entry.reason}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun WarmProfile(
    onLogout: () -> Unit,
    onClockOut: () -> Unit,
) {
    val repo = WarmCareFakeRepo
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        WarmSectionTitle("Your cozy corner", "Maya Santos - Practitioner at Sunrise Clinic.")
        Spacer(Modifier.height(12.dp))
        WarmCard {
            Column {
                Text(text = "Capabilities today", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "View branch data, log sessions, manage inventory. " +
                        "Clock into Harbor Tour or the Medical Mission for view-only relief; ask for a grant to edit.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        WarmCard {
            Column {
                Text(text = "Day controls", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WarmDayStatus.entries.forEach { status ->
                        val on = repo.dayStatus.value == status
                        if (on) {
                            Button(
                                onClick = {},
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) { Text(status.name) }
                        } else {
                            OutlinedButton(
                                onClick = { repo.dayStatus.value = status },
                                shape = RoundedCornerShape(16.dp),
                            ) { Text(status.name) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Flip the banner to preview OPEN, PAST, and REMITTED copy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    repo.clockedIn.value = false
                    onClockOut()
                },
                shape = RoundedCornerShape(16.dp),
            ) { Text("Clock out") }
            OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(16.dp)) { Text("Log out") }
            OutlinedButton(onClick = { repo.reset() }, shape = RoundedCornerShape(16.dp)) { Text("Reset demo") }
        }
    }
}
