package com.companyb.companyapp.proto.contextrail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

@Composable
fun ContextRailApp() {
    val store = remember { seedContextRailStore() }
    logInfo("ContextRail", "context-rail prototype started")
    ContextRailTheme {
        val userId = store.currentUserId.value
        val user = store.currentUser()
        Box(Modifier.fillMaxSize().background(RailPaper)) {
            when {
                userId == null || user == null -> LoginScreen(store)
                user.role == RailRole.ONBOARDING -> LockedScreen(store, user)
                store.currentBranchId.value == null -> BranchSelectScreen(store)
                else -> MainShell(store, user)
            }
        }
    }
}

@Composable
private fun LoginScreen(store: ContextRailStore) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Text("Context Rail", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = RailInk)
        Spacer(Modifier.height(6.dp))
        Text(
            "The list stays put. The right rail follows your selection everywhere.",
            style = MaterialTheme.typography.bodyLarge,
            color = RailMuted,
        )
        Spacer(Modifier.height(24.dp))
        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (person in store.users) {
                PaperCard(
                    title = person.name,
                    subtitle = "${person.role.label} · home ${person.homeBranchId} · slot ${person.slot}",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RailChip(
                            person.role.label,
                            if (person.role == RailRole.ONBOARDING) RailTone.GREY else RailTone.BLUE,
                        )
                        Spacer(Modifier.weight(1f))
                        RailPrimary("Sign in", onClick = {
                            store.currentUserId.value = person.id
                            store.audit(person.name, "LOGIN", "auth:${person.id}", "Signed in to context-rail.")
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.width(600.dp)) {
            RailNote("Fake operators only — any secret works. No network calls, no backend.")
        }
    }
}

@Composable
private fun LockedScreen(store: ContextRailStore, user: RailUser) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RailChip("ONBOARDING", RailTone.GREY)
        Spacer(Modifier.height(16.dp))
        Text("Welcome, ${user.name}.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Locked: the ONBOARDING role carries an empty capability bundle, so nothing derives " +
                "even with a branch assignment. A MANAGER with MANAGE_USERS must grant a real role.",
            style = MaterialTheme.typography.bodyLarge,
            color = RailMuted,
            modifier = Modifier.width(560.dp),
        )
        Spacer(Modifier.height(20.dp))
        RailOutline("Back to sign in", onClick = {
            store.currentUserId.value = null
            store.audit(user.name, "LOGOUT", "auth:${user.id}", "Locked onboarding profile signed out.")
        })
    }
}

@Composable
private fun BranchSelectScreen(store: ContextRailStore) {
    val user = store.currentUser()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)) {
        Text("Pick a Branch", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Signed in as ${user?.name ?: "—"}. Branches hold their own inventory, Sessions, and financial records.",
            color = RailMuted,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(640.dp)) {
            for (branch in store.branches) {
                PaperCard(title = branch.name, subtitle = branch.kind.label) {
                    RailPrimary("Enter ${branch.name}", onClick = {
                        store.currentBranchId.value = branch.id
                        store.audit(user?.name ?: "?", "ENTER_BRANCH", "branch:${branch.id}", "Entered ${branch.name}.")
                    })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        RailQuiet("Sign out", onClick = { store.currentUserId.value = null })
    }
}

@Composable
private fun MainShell(store: ContextRailStore, user: RailUser) {
    val branch = store.currentBranch()
    val day = store.branchDay()
    val pending = store.sessions.count { it.status == RailSessionStatus.PENDING && !it.voided }
    val unread = store.notices.count { !it.read }
    val screen = store.currentScreen.value
    Column(Modifier.fillMaxSize()) {
        BranchDayBanner(day = day, branch = branch, onPickDay = { picked ->
            store.currentDay.value = picked
            store.audit(user.name, "VIEW_DAY", "day:$picked", "Switched branch-day to $picked.")
        })
        Row(Modifier.weight(1f).fillMaxWidth()) {
            NavRail(current = screen, unread = unread, pending = pending, onPick = {
                store.currentScreen.value = it
            })
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(RailPadPage),
            ) {
                when (screen) {
                    RailScreen.HOME -> HomeCenter(store, user)
                    RailScreen.SESSIONS -> SessionsCenter(store, user)
                    RailScreen.CLIENTS -> ClientsCenter(store)
                    RailScreen.FINANCE -> FinanceCenter(store, user)
                    RailScreen.TEAM -> TeamCenter(store)
                    RailScreen.MAIL -> MailCenter(store, user)
                    RailScreen.PROFILE -> ProfileCenter(store, user)
                }
            }
            when (screen) {
                RailScreen.HOME -> HomeInspector(store, user)
                RailScreen.SESSIONS -> SessionsInspector(store, user)
                RailScreen.CLIENTS -> ClientsInspector(store, user)
                RailScreen.FINANCE -> FinanceInspector(store, user)
                RailScreen.TEAM -> TeamInspector(store, user)
                RailScreen.MAIL -> MailInspector(store, user)
                RailScreen.PROFILE -> ProfileInspector(store, user)
            }
        }
        StatusBar(user, branch, day, pending, unread, store.clockedIn.value)
    }
}

@Composable
private fun HomeCenter(store: ContextRailStore, user: RailUser) {
    CenterColumn("Home", "Clock in, then work the relief board. Selection lands in the rail.") {
        PaperCard(title = if (store.clockedIn.value) "Clocked in" else "Clocked out", subtitle = "${user.name} · home ${user.homeBranchId}") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RailPrimary(if (store.clockedIn.value) "Clock out" else "Clock in", onClick = {
                    store.clockedIn.value = !store.clockedIn.value
                    store.audit(user.name, if (store.clockedIn.value) "CLOCK_IN" else "CLOCK_OUT", "attendance", "Clock toggled.")
                })
                RailOutline(if (store.reliefEdit.value) "Relief: edit" else "Relief: view", onClick = {
                    store.reliefEdit.value = !store.reliefEdit.value
                })
            }
        }
        val today = store.sessions.filter { it.branchId == store.currentBranchId.value && it.dayDate == store.currentDay.value }
        PaperCard(title = "Today at a glance", subtitle = "${today.size} Sessions on ${store.currentDay.value}") {
            RailKeyValue("Completed", today.count { it.status == RailSessionStatus.COMPLETED }.toString())
            RailKeyValue("Pending", today.count { it.status == RailSessionStatus.PENDING }.toString())
            RailKeyValue("No-show / Cancelled", today.count { it.status == RailSessionStatus.NO_SHOW || it.status == RailSessionStatus.CANCELLED }.toString())
            RailKeyValue("Voided", today.count { it.voided }.toString())
        }
        PaperCard(title = "Relief board", subtitle = "Duty, Requests, Invites — tap to inspect") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in store.relief) {
                    val selected = store.selectedReliefId.value == item.id
                    SelectableCard(selected = selected, onClick = { store.selectedReliefId.value = item.id }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.person, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            RailChip(item.kind.label, RailTone.BLUE)
                        }
                        Text("${item.branchId} · ${item.date} — ${item.note}", color = RailMuted)
                    }
                }
            }
        }
        RailNote("Relief Duty starts view-only at a non-home Branch; edit needs a grant. Requests broadcast to the Branch; Invites target one person for one future day.")
    }
}

@Composable
private fun HomeInspector(store: ContextRailStore, user: RailUser) {
    val item = store.relief.firstOrNull { it.id == store.selectedReliefId.value }
    InspectorFrame("Home inspector", "Clock state plus the selected relief card.") {
        RailInspectorNote("Clocked ${if (store.clockedIn.value) "in" else "out"} as ${user.name}. Relief access: ${if (store.reliefEdit.value) "edit granted" else "view only"}.")
        if (item == null) {
            RailInspectorNote("Select a relief card on the left — the list stays, this rail moves.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RailDarkChip("${item.kind.label} · ${item.date}")
                Text(item.person, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RailInspectorText)
                Text("${item.branchId} — ${item.note}", color = RailInspectorMuted)
                when (item.kind) {
                    RailReliefKind.REQUEST -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RailPrimary("Grant", onClick = {
                            store.relief.remove(item)
                            store.relief.add(item.copy(kind = RailReliefKind.DUTY, note = "Edit access granted"))
                            store.selectedReliefId.value = item.id
                            store.audit(user.name, "GRANT_RELIEF", "relief:${item.id}", "Granted relief request.")
                            store.notify("Relief granted", "${item.person} granted at ${item.branchId} for ${item.date}.", item.branchId, item.date)
                        })
                        RailOutline("Deny", onClick = {
                            store.relief.remove(item)
                            store.selectedReliefId.value = null
                            store.audit(user.name, "DENY_RELIEF", "relief:${item.id}", "Denied relief request.")
                        })
                    }
                    RailReliefKind.INVITE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RailPrimary("Accept", onClick = {
                            store.relief.remove(item)
                            store.relief.add(item.copy(kind = RailReliefKind.DUTY, note = "Invite accepted — duty written"))
                            store.audit(user.name, "ACCEPT_INVITE", "relief:${item.id}", "Accepted relief invite.")
                        })
                        RailOutline("Decline", onClick = {
                            store.relief.remove(item)
                            store.selectedReliefId.value = null
                            store.audit(user.name, "DECLINE_INVITE", "relief:${item.id}", "Declined relief invite.")
                        })
                    }
                    RailReliefKind.DUTY -> RailInspectorNote("Duty active. Multiple relief workers may hold edit access at one Branch on the same day. Expires 04:00 Manila next day.")
                }
                RailOutline("Withdraw selection", onClick = { store.selectedReliefId.value = null })
            }
        }
    }
}

@Composable
private fun SessionsCenter(store: ContextRailStore, user: RailUser) {
    var showCreate by remember { mutableStateOf(false) }
    var newClient by remember { mutableStateOf("New client") }
    var newTime by remember { mutableStateOf("15:00") }
    var newWalkIn by remember { mutableStateOf(false) }
    val filters = listOf(null, "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED", "VOIDED")
    CenterColumn("Sessions", "Tap a row — detail and actions live in the rail.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RailPrimary("Book session", onClick = { showCreate = true })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (filter in filters) {
                val active = store.sessionFilter.value == filter
                val label = filter ?: "ALL"
                Box(
                    modifier = Modifier.background(
                        if (active) RailForest else RailCard,
                        RoundedCornerShape(999.dp),
                    ).border(1.dp, if (active) RailForest else RailLine, RoundedCornerShape(999.dp))
                        .clickable { store.sessionFilter.value = filter }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) RailForestText else RailInk)
                }
            }
        }
        val rows = store.sessions.filter {
            it.branchId == store.currentBranchId.value && it.dayDate == store.currentDay.value &&
                (store.sessionFilter.value == null || (if (store.sessionFilter.value == "VOIDED") it.voided else it.status.label == store.sessionFilter.value))
        }
        if (rows.isEmpty()) {
            PaperCard(title = "No Sessions", subtitle = "Nothing matches this filter.") {
                Text("Book one, or clear the filter.", color = RailMuted)
            }
        }
        for (session in rows) {
            val selected = store.selectedSessionId.value == session.id
            SelectableCard(selected = selected, onClick = { store.selectedSessionId.value = session.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${session.time} · ${session.clientName}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    RailChip(session.status.label, sessionTone(session.status, session.voided))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${session.id} · ${session.type} · ${peso(session.price)}", color = RailMuted, modifier = Modifier.weight(1f))
                    if (session.walkIn) RailChip("walk-in", RailTone.BLUE)
                    if (session.voided) RailChip("VOIDED", RailTone.GREY)
                }
            }
        }
        RailNote("Walk-in Sessions cannot be marked NO_SHOW or CANCELLED — the rail refuses them with a note.")
    }
    if (showCreate) {
        var localClient by remember { mutableStateOf(newClient) }
        var localTime by remember { mutableStateOf(newTime) }
        var localWalkIn by remember { mutableStateOf(newWalkIn) }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Book session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = localClient, onValueChange = { localClient = it }, label = { Text("Client name") })
                    OutlinedTextField(value = localTime, onValueChange = { localTime = it }, label = { Text("Time") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = localWalkIn, onCheckedChange = { localWalkIn = it })
                        Text("Walk-in")
                    }
                }
            },
            confirmButton = {
                RailPrimary("Book", onClick = {
                    val id = store.nextSessionId()
                    store.sessions.add(
                        RailSession(id, localTime, "c-new", localClient, store.currentBranchId.value ?: "b-makati", store.currentDay.value, if (localWalkIn) "Walk-in" else "Follow-up", RailSessionStatus.PENDING, 1200, user.name, localWalkIn),
                    )
                    store.selectedSessionId.value = id
                    store.audit(user.name, "INSERT", "sessions:$id", "Booked $id for $localClient.")
                    newClient = localClient
                    newTime = localTime
                    newWalkIn = localWalkIn
                    showCreate = false
                })
            },
            dismissButton = { RailQuiet("Cancel", onClick = { showCreate = false }) },
        )
    }
}

@Composable
private fun SessionsInspector(store: ContextRailStore, user: RailUser) {
    val session = store.sessions.firstOrNull { it.id == store.selectedSessionId.value }
    var showVoid by remember { mutableStateOf(false) }
    var voidReason by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf<String?>(null) }
    InspectorFrame("Session inspector", session?.id ?: "Nothing selected") {
        if (session == null) {
            RailInspectorNote("Pick a Session on the left. This rail shows the full record and every action.")
            return@InspectorFrame
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RailDarkChip("${session.status.label}${if (session.voided) " · VOIDED" else ""}")
            Text("${session.time} · ${session.clientName}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RailInspectorText)
            RailDarkKeyValue("Type", "${session.type}${if (session.walkIn) " (walk-in)" else ""}")
            RailDarkKeyValue("Price", peso(session.price))
            RailDarkKeyValue("Practitioners", session.practitioners)
            RailDarkKeyValue("Branch day", "${session.branchId} · ${session.dayDate}")
            if (session.voided) RailInspectorNote("Voided: ${session.voidReason}")
            if (refused != null) RailInspectorNote(refused ?: "")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RailPrimary("Complete", onClick = {
                    refused = null
                    replaceSession(store, session, session.copy(status = RailSessionStatus.COMPLETED))
                    store.audit(user.name, "UPDATE", "sessions:${session.id}", "${session.status.label} -> COMPLETED.")
                })
                RailOutline("No-show", onClick = {
                    if (session.walkIn) {
                        refused = "Refused: walk-in Sessions cannot be NO_SHOW."
                    } else {
                        refused = null
                        replaceSession(store, session, session.copy(status = RailSessionStatus.NO_SHOW))
                        store.audit(user.name, "UPDATE", "sessions:${session.id}", "${session.status.label} -> NO_SHOW.")
                    }
                })
                RailOutline("Cancel", onClick = {
                    if (session.walkIn) {
                        refused = "Refused: walk-in Sessions cannot be CANCELLED."
                    } else {
                        refused = null
                        replaceSession(store, session, session.copy(status = RailSessionStatus.CANCELLED))
                        store.audit(user.name, "UPDATE", "sessions:${session.id}", "${session.status.label} -> CANCELLED.")
                    }
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!session.voided) {
                    RailOutline("Void with reason", onClick = { showVoid = true })
                } else {
                    RailPrimary("Unvoid", onClick = {
                        replaceSession(store, session, session.copy(voided = false, voidReason = ""))
                        store.audit(user.name, "UNVOID", "sessions:${session.id}", "Unvoided.")
                    })
                }
            }
        }
    }
    if (showVoid) {
        AlertDialog(
            onDismissRequest = { showVoid = false },
            title = { Text("Void ${session?.id ?: ""}") },
            text = {
                Column {
                    Text("Void excludes the Session from finance but keeps the record. A reason is required.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = voidReason, onValueChange = { voidReason = it }, label = { Text("Reason") })
                }
            },
            confirmButton = {
                RailPrimary("Void", onClick = {
                    val current = store.sessions.firstOrNull { it.id == store.selectedSessionId.value }
                    if (current != null && voidReason.isNotBlank()) {
                        replaceSession(store, current, current.copy(voided = true, voidReason = voidReason))
                        store.audit(user.name, "VOID", "sessions:${current.id}", "Voided: $voidReason")
                    }
                    showVoid = false
                    voidReason = ""
                })
            },
            dismissButton = { RailQuiet("Cancel", onClick = { showVoid = false }) },
        )
    }
}

private fun replaceSession(store: ContextRailStore, old: RailSession, new: RailSession) {
    val index = store.sessions.indexOfFirst { it.id == old.id }
    if (index >= 0) store.sessions[index] = new
}

@Composable
private fun ClientsCenter(store: ContextRailStore) {
    CenterColumn("Clients", "Global registry — tap a row to inspect. List never navigates.") {
        RailNote("A Client holds at most one PENDING Session at a time — starred below. Anonymized rows keep gender and age only.")
        for (client in store.clients) {
            val selected = store.selectedClientId.value == client.id
            SelectableCard(selected = selected, onClick = { store.selectedClientId.value = client.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (client.anonymized) "Anonymized record" else client.name,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (client.pendingSessionId != null) RailChip("* PENDING", RailTone.AMBER)
                    if (client.anonymized) RailChip("anonymized", RailTone.GREY)
                }
                Text("${client.gender} · ${client.age} · ${client.phone}", color = RailMuted)
            }
        }
    }
}

@Composable
private fun ClientsInspector(store: ContextRailStore, user: RailUser) {
    val client = store.clients.firstOrNull { it.id == store.selectedClientId.value }
    var confirm by remember { mutableStateOf(false) }
    InspectorFrame("Client inspector", client?.id ?: "Nothing selected") {
        if (client == null) {
            RailInspectorNote("Select a Client — PII, Sessions, and anonymize live here.")
            return@InspectorFrame
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (client.anonymized) "Anonymized record" else client.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RailInspectorText)
            RailDarkKeyValue("Gender", client.gender)
            RailDarkKeyValue("Age", client.age.toString())
            RailDarkKeyValue("Contact", if (client.anonymized) "withheld" else client.phone)
            RailDarkKeyValue("Pending", client.pendingSessionId ?: "none")
            if (client.anonymized) {
                RailInspectorNote("Anonymized view: soft-deleted with PII nullified; gender and age retained for reporting.")
            } else {
                RailPrimary("Anonymize", onClick = { confirm = true })
            }
        }
    }
    if (confirm && client != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Anonymize ${client.name}?") },
            text = { Text("Soft-delete with PII nullification. Gender and age stay for reporting.") },
            confirmButton = {
                RailPrimary("Anonymize", onClick = {
                    val index = store.clients.indexOfFirst { it.id == client.id }
                    if (index >= 0) store.clients[index] = client.copy(name = "Record ${client.id}", phone = "withheld", anonymized = true)
                    store.audit(user.name, "ANONYMIZE", "clients:${client.id}", "Anonymized client record.")
                    confirm = false
                })
            },
            dismissButton = { RailQuiet("Keep", onClick = { confirm = false }) },
        )
    }
}

@Composable
private fun FinanceCenter(store: ContextRailStore, user: RailUser) {
    var productName by remember { mutableStateOf("Herbal liniment 100ml") }
    var productQty by remember { mutableStateOf("2") }
    CenterColumn("Finance", "Drafts and snapshots — select one to act in the rail.") {
        PaperCard(title = "Session draft", subtitle = "Net = completed income − compensation − expenses") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (remit in store.remittances.filter { it.kind == RailRemitKind.SESSION }) {
                    val selected = store.selectedRemitId.value == remit.id
                    SelectableCard(selected = selected, onClick = { store.selectedRemitId.value = remit.id }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${remit.id} · ${remit.dayDate}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            RailChip(remit.state.label, if (remit.state == RailSubmission.DRAFT) RailTone.AMBER else RailTone.GREEN)
                        }
                        Text("${peso(remit.gross)} gross − ${peso(remit.deductions)} = ${peso(remit.gross - remit.deductions)} net", color = RailMuted)
                    }
                }
            }
        }
        PaperCard(title = "Product draft", subtitle = "Unit price × quantity, lines below") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (line in store.productLines) {
                    RailKeyValue("${line.name} × ${line.qty}", peso(line.qty * line.unitPrice))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = productName, onValueChange = { productName = it }, label = { Text("Item") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = productQty, onValueChange = { productQty = it }, label = { Text("Qty") }, modifier = Modifier.width(80.dp))
                    RailPrimary("Add", onClick = {
                        val qty = productQty.toIntOrNull() ?: 1
                        store.productLines.add(RailProductLine("p-${store.productLines.size + 1}", productName, qty, 350))
                        store.audit(user.name, "INSERT", "products:$productName", "Added $qty × $productName.")
                    })
                }
                for (remit in store.remittances.filter { it.kind == RailRemitKind.PRODUCT }) {
                    val selected = store.selectedRemitId.value == remit.id
                    SelectableCard(selected = selected, onClick = { store.selectedRemitId.value = remit.id }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${remit.id} · ${remit.dayDate}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            RailChip(remit.state.label, if (remit.state == RailSubmission.DRAFT) RailTone.AMBER else RailTone.GREEN)
                        }
                        if (remit.snapshotId != null) Text("Snapshot ${remit.snapshotId}", color = RailMuted)
                    }
                }
            }
        }
        PaperCard(title = "Commission split", subtitle = "Pooled per Branch day, split equally") {
            Text("Product commissions pool per Branch day and split equally among practitioners and coordinators clocked in at sold_at. Manual inclusions and exclusions can override. Separate from compensation — not subject to remittance.", color = RailMuted)
            for (name in store.commissionIncluded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, modifier = Modifier.weight(1f))
                    RailQuiet("Remove", onClick = {
                        store.commissionIncluded.remove(name)
                        store.audit(user.name, "UPDATE", "commission", "Excluded $name from split.")
                    })
                }
            }
        }
    }
}

@Composable
private fun FinanceInspector(store: ContextRailStore, user: RailUser) {
    val remit = store.remittances.firstOrNull { it.id == store.selectedRemitId.value }
    var undoReason by remember { mutableStateOf("") }
    var showUndo by remember { mutableStateOf(false) }
    InspectorFrame("Remittance inspector", remit?.id ?: "Nothing selected") {
        if (remit == null) {
            RailInspectorNote("Select a draft or snapshot — submit and Undo live here.")
            return@InspectorFrame
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RailDarkChip("${remit.kind.label} · ${remit.state.label}")
            Text("${remit.id} · ${remit.dayDate}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RailInspectorText)
            RailDarkKeyValue("Gross", peso(remit.gross))
            RailDarkKeyValue("Deductions", peso(remit.deductions))
            RailDarkKeyValue("Net", peso(remit.gross - remit.deductions))
            Text(remit.note, color = RailInspectorMuted)
            if (remit.snapshotId != null) RailInspectorNote("Immutable snapshot ${remit.snapshotId} frozen at ${remit.submittedAt ?: "—"}. Later edits never rewrite it.")
            if (remit.state == RailSubmission.DRAFT) {
                RailPrimary("Submit ${remit.id}", onClick = {
                    val index = store.remittances.indexOfFirst { it.id == remit.id }
                    val snap = "SNAP-${(1000..9999).random()}"
                    if (index >= 0) store.remittances[index] = remit.copy(state = RailSubmission.SUBMITTED, snapshotId = snap, submittedAt = "2026-09-09 22:10 Asia/Manila")
                    store.audit(user.name, "SUBMIT", "remittance:${remit.id}", "Submitted; snapshot $snap frozen.")
                    store.notify("Remittance submitted", "${remit.id} submitted. Snapshot $snap frozen.", remit.branchId, remit.dayDate)
                })
            } else {
                RailPrimary("Undo within 48h", onClick = { showUndo = true })
            }
        }
    }
    if (showUndo && remit != null) {
        AlertDialog(
            onDismissRequest = { showUndo = false },
            title = { Text("Undo ${remit.id}") },
            text = {
                Column {
                    Text("Undo returns the remittance to Draft, unlocks covered days, deletes the snapshot. Needs a reason, inside 48 hours.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = undoReason, onValueChange = { undoReason = it }, label = { Text("Reason") })
                }
            },
            confirmButton = {
                RailPrimary("Undo", onClick = {
                    if (undoReason.isNotBlank()) {
                        val index = store.remittances.indexOfFirst { it.id == remit.id }
                        if (index >= 0) store.remittances[index] = remit.copy(state = RailSubmission.DRAFT, snapshotId = null, submittedAt = null)
                        store.audit(user.name, "UNDO", "remittance:${remit.id}", "Undone: $undoReason")
                    }
                    showUndo = false
                    undoReason = ""
                })
            },
            dismissButton = { RailQuiet("Keep submitted", onClick = { showUndo = false }) },
        )
    }
}

@Composable
private fun TeamCenter(store: ContextRailStore) {
    CenterColumn("Team", "Slot-ordered roster — relief sorts last. Tap to inspect.") {
        val ordered = store.staff.sortedWith(compareBy({ it.relief }, { it.slot }))
        for (member in ordered) {
            val selected = store.selectedStaffId.value == member.id
            SelectableCard(selected = selected, onClick = { store.selectedStaffId.value = member.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    RailChip(member.role.label, if (member.role == RailRole.ONBOARDING) RailTone.GREY else RailTone.BLUE)
                }
                Text("Slot ${member.slot} · home ${member.homeBranchId}${if (member.relief) " · relief" else ""} · ${if (member.clockedIn) "in" else "out"}", color = RailMuted)
            }
        }
        RailNote("Branch Slot is cosmetic order (1 = senior). Relief practitioners sort after home slots.")
    }
}

@Composable
private fun TeamInspector(store: ContextRailStore, user: RailUser) {
    val member = store.staff.firstOrNull { it.id == store.selectedStaffId.value }
    InspectorFrame("Member inspector", member?.name ?: "Nobody selected") {
        if (member == null) {
            RailInspectorNote("Pick a teammate — role bundle and presence live here.")
            return@InspectorFrame
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RailDarkChip("${member.role.label} · slot ${member.slot}")
            Text(member.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RailInspectorText)
            RailDarkKeyValue("Home", member.homeBranchId)
            RailDarkKeyValue("Presence", if (member.clockedIn) "clocked in" else "clocked out")
            RailDarkKeyValue("Relief", if (member.relief) "yes — sorts last" else "home crew")
            Text("Capabilities: ${roleCapabilities(member.role).ifEmpty { listOf("— locked (empty bundle)") }.joinToString(", ")}", color = RailInspectorMuted)
            if (member.role == RailRole.ONBOARDING) {
                RailInspectorNote("ONBOARDING carries zero capabilities — functionally locked until MANAGE_USERS grants a real role.")
            }
            if (user.role == RailRole.MANAGER && member.role == RailRole.ONBOARDING) {
                RailPrimary("Grant Practitioner", onClick = {
                    val index = store.staff.indexOfFirst { it.id == member.id }
                    if (index >= 0) store.staff[index] = member.copy(role = RailRole.PRACTITIONER)
                    store.audit(user.name, "GRANT_ROLE", "users:${member.id}", "Granted Practitioner.")
                })
            }
        }
    }
}

@Composable
private fun MailCenter(store: ContextRailStore, user: RailUser) {
    CenterColumn("Mailbox", "Tap a notice — it marks read and opens in the rail.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RailOutline("Mark all read", onClick = {
                for (index in store.notices.indices) {
                    store.notices[index] = store.notices[index].copy(read = true)
                }
                store.audit(user.name, "READ_ALL", "notifications", "Drained mailbox.")
            })
        }
        for (notice in store.notices) {
            val selected = store.selectedNoticeId.value == notice.id
            SelectableCard(selected = selected, onClick = {
                store.selectedNoticeId.value = notice.id
                val index = store.notices.indexOfFirst { it.id == notice.id }
                if (index >= 0) store.notices[index] = notice.copy(read = true)
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(notice.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (!notice.read) RailChip("unread", RailTone.AMBER) else RailChip("read", RailTone.GREY)
                }
                Text(notice.body, color = RailMuted)
            }
        }
        PaperCard(title = "Audit log", subtitle = "Newest first — every mutation appends actor + action") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (entry in store.audits.take(12)) {
                    Text("${entry.time} · ${entry.actor} · ${entry.action} · ${entry.target}", fontWeight = FontWeight.SemiBold)
                    Text(entry.detail, color = RailMuted)
                }
            }
        }
    }
}

@Composable
private fun MailInspector(store: ContextRailStore, user: RailUser) {
    val notice = store.notices.firstOrNull { it.id == store.selectedNoticeId.value }
    InspectorFrame("Notice inspector", notice?.id ?: "Nothing selected") {
        if (notice == null) {
            RailInspectorNote("Select a message — relief events name the Branch and day, and tap through to that day.")
            return@InspectorFrame
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RailDarkChip(if (notice.read) "read" else "unread")
            Text(notice.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RailInspectorText)
            Text(notice.body, color = RailInspectorText)
            RailDarkKeyValue("Branch", notice.branchId ?: "—")
            RailDarkKeyValue("Day", notice.dayDate ?: "—")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RailOutline("Jump to day", onClick = {
                    if (notice.dayDate != null) store.currentDay.value = notice.dayDate
                    if (notice.branchId != null) store.currentBranchId.value = notice.branchId
                    store.currentScreen.value = RailScreen.HOME
                    store.audit(user.name, "NAVIGATE", "day:${notice.dayDate}", "Tapped through from ${notice.id}.")
                })
                RailQuiet("Toggle read", onClick = {
                    val index = store.notices.indexOfFirst { it.id == notice.id }
                    if (index >= 0) store.notices[index] = notice.copy(read = !notice.read)
                })
            }
            RailInspectorNote("Read rows are kept forever as history. Relief notices are written in the same transaction as the change that caused them.")
        }
    }
}

@Composable
private fun ProfileCenter(store: ContextRailStore, user: RailUser) {
    CenterColumn("Profile", "You — capabilities, presence, and sign-out.") {
        PaperCard(title = user.name, subtitle = "${user.role.label} · slot ${user.slot}") {
            RailKeyValue("Home Branch", user.homeBranchId)
            RailKeyValue("Presence", if (store.clockedIn.value) "clocked in" else "clocked out")
            RailKeyValue("Capabilities", if (user.capabilities.isEmpty()) "— locked" else "${user.capabilities.size} codes")
        }
        PaperCard(title = "Capability bundle", subtitle = "Runtime checks use capabilities, not role names") {
            if (user.capabilities.isEmpty()) {
                Text("Empty bundle — ONBOARDING derives nothing.", color = RailMuted)
            }
            for (cap in user.capabilities) {
                Text("· $cap")
            }
        }
    }
}

@Composable
private fun ProfileInspector(store: ContextRailStore, user: RailUser) {
    InspectorFrame("Account inspector", user.name) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RailInspectorNote("Signing out returns to the operator picker. Clock-out ends the day's attendance; compensation for relief days pays from the relief Branch drawer.")
            RailPrimary(if (store.clockedIn.value) "Clock out" else "Clock in", onClick = {
                store.clockedIn.value = !store.clockedIn.value
                store.audit(user.name, if (store.clockedIn.value) "CLOCK_IN" else "CLOCK_OUT", "attendance", "Toggled from profile.")
            })
            RailOutline("Switch Branch", onClick = {
                store.currentBranchId.value = null
                store.audit(user.name, "LEAVE_BRANCH", "branch", "Opened branch picker.")
            })
            RailOutline("Log out", onClick = {
                store.currentUserId.value = null
                store.currentBranchId.value = null
                store.audit(user.name, "LOGOUT", "auth:${user.id}", "Signed out from context-rail.")
            })
        }
    }
}
