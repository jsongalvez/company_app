package com.companyb.companyapp.proto.carddeck

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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DeckHomeScreen() {
    val repo = CardDeckFakeRepo
    var inviteName by remember { mutableStateOf("") }
    var requestBranch by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckHeader(
            "Good shift - pick a card",
            "Clock in at home, or ride relief at another table",
            if (repo.clockedIn.value) "CLOCKED IN" else "OFF DUTY",
        )
        DeckNoteCard("Home table: ${repo.branchName(repo.clockedBranchId.value)}. Relief duty starts view-only; a relief grant unlocks edits.")
        DeckRow(
            cards = repo.branches.toList(),
            emptyTitle = "No tables",
            emptyBody = "Fresh deck has no branches.",
            keyOf = { it.id },
        ) { branch ->
            val isHome = branch.id == repo.clockedBranchId.value
            PlayingCard(
                accent = if (isHome) SuitMint else SuitSky,
                rank = branch.name.take(1),
                title = branch.name,
                meta = branch.kind + if (isHome) " - home table" else "",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeckChip(if (repo.clockedIn.value && isHome) "ON DUTY" else "OFF", if (repo.clockedIn.value && isHome) SuitMint else SuitSlate)
                }
                Spacer(Modifier.height(10.dp))
                if (!repo.clockedIn.value) {
                    Button(
                        onClick = {
                            repo.clockedIn.value = true
                            repo.clockedBranchId.value = branch.id
                            repo.log("CLOCK_IN", branch.name)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SuitMint),
                    ) {
                        Text("Clock in here")
                    }
                } else if (isHome) {
                    OutlinedButton(
                        onClick = {
                            repo.clockedIn.value = false
                            repo.log("CLOCK_OUT", branch.name)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clock out")
                    }
                } else {
                    Text(text = "Ride relief to deal at this table.", fontSize = 12.sp, color = InkSoft)
                }
            }
        }
        DeckHeader("Relief deck", "Requests go out, invites come in - all name branch and day", "${repo.relief.size} cards")
        DeckRow(
            cards = repo.relief.toList(),
            emptyTitle = "Relief deck clear",
            emptyBody = "No requests or invites on the table.",
            keyOf = { it.id },
        ) { item ->
            PlayingCard(accent = SuitAmber, rank = item.kind.take(1), title = item.kind, meta = "${item.branch} - ${item.date}") {
                Text(text = "${item.who} - ${item.state}", fontSize = 13.sp, color = InkText)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val idx = repo.relief.indexOfFirst { it.id == item.id }
                            if (idx >= 0) repo.relief[idx] = item.copy(state = "Granted")
                            repo.log("RELIEF_GRANT", "${item.branch} ${item.date}")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SuitAmber),
                    ) {
                        Text("Grant")
                    }
                    OutlinedButton(
                        onClick = {
                            repo.relief.removeAll { it.id == item.id }
                            repo.log("RELIEF_DECLINE", "${item.branch} ${item.date}")
                        },
                    ) {
                        Text("Fold")
                    }
                }
            }
        }
        PlayingCard(accent = SuitSky, rank = "+", title = "Deal new relief cards", meta = "Broadcast a request or invite help") {
            OutlinedTextField(
                value = requestBranch,
                onValueChange = { requestBranch = it },
                label = { Text("Request relief at branch / day") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (requestBranch.isBlank()) return@Button
                        repo.relief.add(
                            DeckRelief("f${repo.relief.size + 1}x", "Relief request", repo.currentUserName.value, requestBranch, "Today", "Broadcast - waiting for grant"),
                        )
                        repo.log("RELIEF_REQUEST", requestBranch)
                        requestBranch = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuitSky),
                ) {
                    Text("Broadcast request")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = inviteName,
                onValueChange = { inviteName = it },
                label = { Text("Invite user by name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (inviteName.isBlank()) return@OutlinedButton
                    repo.relief.add(
                        DeckRelief("f${repo.relief.size + 1}y", "Relief invite", inviteName, repo.branchName(repo.clockedBranchId.value), "Sat", "Invite waiting for reply"),
                    )
                    repo.log("RELIEF_INVITE", inviteName)
                    inviteName = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Send invite")
            }
        }
    }
}

@Composable
internal fun DeckSessionsScreen() {
    val repo = CardDeckFakeRepo
    var filter by remember { mutableStateOf<DeckSessionStatus?>(null) }
    var voidTarget by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var newClient by remember { mutableStateOf("") }
    val visible = repo.sessions.filter { filter == null || it.status == filter }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckHeader("Sessions deck", "PENDING flows to COMPLETED, NO_SHOW or CANCELLED", "${visible.size} cards")
        DeckNoteCard("House rule: walk-in sessions cannot be marked NO_SHOW or CANCELLED.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("All", filter == null) { filter = null }
            DeckSessionStatus.entries.forEach { status ->
                FilterPill(status.name, filter == status) { filter = status }
            }
        }
        DeckRow(
            cards = visible,
            emptyTitle = "No cards in this suit",
            emptyBody = "Try another filter - the deck is not empty.",
            keyOf = { it.id },
            cardWidth = 340,
        ) { session ->
            val accent = when (session.status) {
                DeckSessionStatus.PENDING -> SuitAmber
                DeckSessionStatus.COMPLETED -> SuitMint
                DeckSessionStatus.NO_SHOW -> SuitSlate
                DeckSessionStatus.CANCELLED -> SuitCoral
            }
            PlayingCard(
                accent = accent,
                rank = session.clientName.take(1),
                title = session.clientName,
                meta = "${session.id} - ${session.time} - ${session.type}${if (session.walkIn) " - walk-in" else ""}",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeckChip(session.status.name, accent)
                    if (session.voided) DeckChip("VOIDED", SuitCoral)
                }
                Spacer(Modifier.height(8.dp))
                Text(text = "PHP ${session.price} at ${repo.branchName(session.branchId)}", fontSize = 13.sp, color = InkText)
                if (session.voided) {
                    Text(text = "Void reason: ${session.voidReason}", fontSize = 12.sp, color = SuitCoral)
                }
                Spacer(Modifier.height(10.dp))
                if (!session.voided) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeckSessionStatus.entries.filter { it != session.status }.forEach { next ->
                            val blocked = session.walkIn && (next == DeckSessionStatus.NO_SHOW || next == DeckSessionStatus.CANCELLED)
                            if (!blocked) {
                                OutlinedButton(
                                    onClick = {
                                        val idx = repo.sessions.indexOfFirst { it.id == session.id }
                                        if (idx >= 0) repo.sessions[idx] = session.copy(status = next)
                                        repo.log("SESSION_${next.name}", session.id)
                                    },
                                ) {
                                    Text(next.name, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (voidTarget == session.id) {
                        OutlinedTextField(
                            value = voidReason,
                            onValueChange = { voidReason = it },
                            label = { Text("Void reason (required)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (voidReason.isBlank()) return@Button
                                    val idx = repo.sessions.indexOfFirst { it.id == session.id }
                                    if (idx >= 0) repo.sessions[idx] = session.copy(voided = true, voidReason = voidReason)
                                    repo.log("SESSION_VOID", session.id, voidReason)
                                    voidTarget = null
                                    voidReason = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuitCoral),
                            ) {
                                Text("Void it")
                            }
                            TextButton(onClick = { voidTarget = null }) { Text("Keep") }
                        }
                    } else {
                        TextButton(onClick = { voidTarget = session.id }) { Text("Void with reason", color = SuitCoral) }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val idx = repo.sessions.indexOfFirst { it.id == session.id }
                            if (idx >= 0) repo.sessions[idx] = session.copy(voided = false, voidReason = "")
                            repo.log("SESSION_UNVOID", session.id)
                        },
                    ) {
                        Text("Unvoid")
                    }
                }
            }
        }
        PlayingCard(accent = SuitMint, rank = "+", title = "Deal a walk-in card", meta = "New PENDING session at this table") {
            OutlinedTextField(
                value = newClient,
                onValueChange = { newClient = it },
                label = { Text("Client name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (newClient.isBlank()) return@Button
                    repo.sessions.add(
                        0,
                        DeckSession("s${repo.sessions.size + 1}x", newClient, repo.clockedBranchId.value, DeckSessionStatus.PENDING, "Walk-in", 600, true, time = "Now"),
                    )
                    repo.log("SESSION_CREATE", newClient)
                    newClient = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = SuitMint),
            ) {
                Text("Add PENDING session")
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (selected) TableRail else FeltMuted,
        )
    }
}

@Composable
internal fun DeckClientsScreen() {
    val repo = CardDeckFakeRepo
    var showAnon by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckHeader("Clients deck", "Global people - shared across every table", "${repo.clients.size} cards")
        DeckNoteCard("House rules: a client holds at most one PENDING session at a time. Anonymized view keeps gender and age for reporting.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Anonymized view:", color = FeltOnDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { showAnon = !showAnon }) { Text(if (showAnon) "Show names" else "Anonymize") }
        }
        DeckRow(
            cards = repo.clients.toList(),
            emptyTitle = "No client cards",
            emptyBody = "Fresh deck has no clients.",
            keyOf = { it.id },
        ) { client ->
            val display = if (showAnon || client.anonymized) "Client ${client.id.uppercase()} (anonymized)" else client.name
            PlayingCard(accent = SuitSky, rank = display.take(2).uppercase(), title = display, meta = client.detail) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (client.hasPending) DeckChip("1 PENDING", SuitAmber) else DeckChip("No pending", SuitMint)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val idx = repo.clients.indexOfFirst { it.id == client.id }
                        if (idx >= 0) repo.clients[idx] = client.copy(anonymized = !client.anonymized)
                        repo.log(if (client.anonymized) "CLIENT_DEANONYMIZE" else "CLIENT_ANONYMIZE", client.id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (client.anonymized) "Restore PII (demo)" else "Anonymize (keeps gender + age)")
                }
            }
        }
    }
}

@Composable
internal fun DeckFinanceScreen() {
    val repo = CardDeckFakeRepo
    var undoTarget by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckHeader("Money deck", "SESSION net income and PRODUCT price x quantity, side by side", "${repo.remittances.size} cards")
        DeckNoteCard("Commission split: product commissions pool per branch day and split equally among practitioners and coordinators clocked in at sold_at time. Separate from compensation, never remitted.")
        DeckRow(
            cards = repo.remittances.toList(),
            emptyTitle = "No money cards",
            emptyBody = "Draft a remittance to start the money deck.",
            keyOf = { it.id },
            cardWidth = 360,
        ) { remit ->
            val accent = if (remit.kind == DeckRemitKind.SESSION) SuitMint else SuitLilac
            PlayingCard(
                accent = accent,
                rank = if (remit.kind == DeckRemitKind.SESSION) "S" else "P",
                title = "${remit.kind.name} - PHP ${remit.amount}",
                meta = "${remit.dayLabel} - ${remit.status.name}",
            ) {
                if (remit.snapshot.isNotEmpty()) {
                    Text(text = "Snapshot: ${remit.snapshot}", fontSize = 12.sp, color = InkSoft)
                    Text(text = "Filed ${remit.submittedAt} - undo within 48h", fontSize = 12.sp, color = InkSoft)
                } else {
                    Text(
                        text = if (remit.kind == DeckRemitKind.SESSION) "Net income after compensation and expenses." else "Unit price x quantity.",
                        fontSize = 12.sp,
                        color = InkSoft,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (remit.status == DeckRemitStatus.DRAFT) {
                    Button(
                        onClick = {
                            val idx = repo.remittances.indexOfFirst { it.id == remit.id }
                            if (idx >= 0) {
                                repo.remittances[idx] = remit.copy(
                                    status = DeckRemitStatus.SUBMITTED,
                                    snapshot = "Frozen P&L ${remit.dayLabel} = PHP ${remit.amount}",
                                    submittedAt = "demo hour 0",
                                )
                            }
                            repo.log("REMIT_SUBMIT", remit.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                    ) {
                        Text("Submit - seal snapshot")
                    }
                } else if (undoTarget == remit.id) {
                    OutlinedTextField(
                        value = undoReason,
                        onValueChange = { undoReason = it },
                        label = { Text("Undo reason (required)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (undoReason.isBlank()) return@Button
                                val idx = repo.remittances.indexOfFirst { it.id == remit.id }
                                if (idx >= 0) {
                                    repo.remittances[idx] = remit.copy(status = DeckRemitStatus.DRAFT, snapshot = "", submittedAt = "")
                                }
                                repo.log("REMIT_UNDO", remit.id, undoReason)
                                undoTarget = null
                                undoReason = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuitCoral),
                        ) {
                            Text("Undo (48h)")
                        }
                        TextButton(onClick = { undoTarget = null }) { Text("Keep sealed") }
                    }
                } else {
                    OutlinedButton(onClick = { undoTarget = remit.id }, modifier = Modifier.fillMaxWidth()) {
                        Text("Undo within 48h")
                    }
                }
            }
        }
        PlayingCard(accent = SuitLilac, rank = "+", title = "Draft fresh money cards", meta = "Drafts overlap freely") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckRemitKind.entries.forEach { kind ->
                    Button(
                        onClick = {
                            repo.remittances.add(
                                DeckRemittance(repo.nextRemitId(), kind, DeckRemitStatus.DRAFT, if (kind == DeckRemitKind.SESSION) 3200 else 900, "Today at ${repo.branchName(repo.clockedBranchId.value)}"),
                            )
                            repo.log("REMIT_DRAFT", kind.name)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (kind == DeckRemitKind.SESSION) SuitMint else SuitLilac,
                        ),
                    ) {
                        Text("Draft ${kind.name}")
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeckTeamScreen() {
    val repo = CardDeckFakeRepo
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckHeader("Team deck", "Users and roles - who can touch which cards", "${repo.users.size} cards")
        DeckNoteCard("ONBOARDING holds an empty capability bundle: locked out until MANAGE_USERS grants a real role. MANAGER is a superset of Coordinator; Accountant reads everything, edits nothing.")
        DeckRow(
            cards = repo.users.toList(),
            emptyTitle = "No team cards",
            emptyBody = "Fresh deck has no users.",
            keyOf = { it.id },
        ) { user ->
            val accent = if (user.onboarding) SuitSlate else SuitLilac
            PlayingCard(
                accent = accent,
                rank = user.name.take(1),
                title = user.name,
                meta = "${user.role} - home ${repo.branchName(user.homeBranchId)}",
            ) {
                if (user.onboarding) {
                    DeckChip("LOCKED - empty bundle", SuitSlate)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val idx = repo.users.indexOfFirst { it.id == user.id }
                            if (idx >= 0) repo.users[idx] = user.copy(role = "Practitioner", onboarding = false)
                            repo.log("ROLE_GRANT", user.id, "Practitioner")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SuitLilac),
                    ) {
                        Text("Grant Practitioner")
                    }
                } else {
                    DeckChip(user.role, accent)
                    Spacer(Modifier.height(8.dp))
                    Text(text = roleBlurb(user.role), fontSize = 12.sp, color = InkSoft)
                }
            }
        }
    }
}

private fun roleBlurb(role: String): String = when (role) {
    "Practitioner" -> "Logs sessions, manages inventory, sees clients."
    "Coordinator" -> "Runs finance and remittance; sole editor of PAST and REMITTED records."
    "MANAGER" -> "Coordinator powers plus user management and delegate assignment."
    "Accountant" -> "Read-only: all branches, zero edits."
    else -> "Custom bundle of capabilities."
}

@Composable
internal fun DeckMailScreen() {
    val repo = CardDeckFakeRepo
    val cards = repo.mailbox.toList()
    val unread = cards.count { !it.read }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckHeader("Mailbox deck", "Every alert kept per recipient - read rows stay forever", "$unread unread")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    cards.filter { !it.read }.forEach { mail ->
                        val idx = repo.mailbox.indexOfFirst { it.id == mail.id }
                        if (idx >= 0) repo.mailbox[idx] = mail.copy(read = true)
                    }
                    repo.log("MAIL_READ_ALL", "$unread cards")
                },
            ) {
                Text("Mark all read")
            }
        }
        DeckRow(
            cards = cards,
            emptyTitle = "Mailbox empty",
            emptyBody = "No notifications dealt yet.",
            keyOf = { it.id },
            cardWidth = 340,
        ) { mail ->
            PlayingCard(
                accent = if (mail.read) SuitSlate else SuitAmber,
                rank = if (mail.read) "R" else "!",
                title = mail.title,
                meta = mail.day,
            ) {
                Text(text = mail.body, fontSize = 13.sp, color = InkText)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val idx = repo.mailbox.indexOfFirst { it.id == mail.id }
                        if (idx >= 0) repo.mailbox[idx] = mail.copy(read = !mail.read)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (mail.read) "Mark unread" else "Mark read")
                }
            }
        }
    }
}

@Composable
internal fun DeckAuditScreen() {
    val repo = CardDeckFakeRepo
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckHeader("Audit deck", "Immutable trail - who changed what, when, and why", "${repo.audits.size} cards")
        if (repo.audits.isEmpty()) {
            DeckNoteCard("No trail yet. Clock in, void a session, or submit a remittance to deal audit cards.")
        }
        DeckRow(
            cards = repo.audits.toList(),
            emptyTitle = "No audit cards",
            emptyBody = "Nothing mutated yet this demo.",
            keyOf = { it.id },
            cardWidth = 340,
        ) { entry ->
            PlayingCard(accent = SuitSlate, rank = entry.action.take(1), title = entry.action, meta = "${entry.whenText} - ${entry.actor}") {
                Text(text = entry.record, fontSize = 13.sp, color = InkText)
                if (entry.reason.isNotEmpty()) {
                    Text(text = "Reason: ${entry.reason}", fontSize = 12.sp, color = SuitCoral)
                }
            }
        }
    }
}

@Composable
internal fun DeckProfileScreen(onLogout: () -> Unit, onReset: () -> Unit) {
    val repo = CardDeckFakeRepo
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckHeader("Your card", "Profile, day controls, and the exit door", "1 card")
        PlayingCard(accent = SuitCoral, rank = repo.currentUserName.value.take(1), title = repo.currentUserName.value, meta = "Practitioner at ${repo.branchName(repo.clockedBranchId.value)}") {
            Text(
                text = "Status: ${if (repo.clockedIn.value) "clocked in" else "off duty"} - day ${repo.dayStatus.value.name}.",
                fontSize = 13.sp,
                color = InkText,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn.value) {
                    OutlinedButton(
                        onClick = {
                            repo.clockedIn.value = false
                            repo.log("CLOCK_OUT", repo.branchName(repo.clockedBranchId.value))
                        },
                    ) {
                        Text("Clock out")
                    }
                }
                Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = SuitCoral)) {
                    Text("Log out")
                }
            }
        }
        PlayingCard(accent = SuitAmber, rank = "D", title = "Flip the branch day", meta = "Demo control - real days flip at 04:00 Asia/Manila") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckDayStatus.entries.forEach { status ->
                    OutlinedButton(
                        onClick = {
                            repo.dayStatus.value = status
                            repo.log("DAY_FLIP", status.name)
                        },
                    ) {
                        Text(status.name, fontSize = 11.sp, fontWeight = if (repo.dayStatus.value == status) FontWeight.Black else FontWeight.Normal)
                    }
                }
            }
        }
        PlayingCard(accent = SuitSlate, rank = "R", title = "Fresh deck", meta = "Reset every card to the opening hand") {
            OutlinedButton(
                onClick = {
                    repo.reset()
                    onReset()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset demo data")
            }
        }
    }
}
