package com.companyb.companyapp.proto.financecockpit

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

@Composable
fun FinanceCockpitApp() {
    val store = remember { seedCockpitStore() }
    logInfo("FinanceCockpit", "finance-cockpit prototype started")
    CockpitTheme {
        val user = store.currentUser()
        Box(Modifier.fillMaxSize().background(CockpitColors.Paper)) {
            when {
                store.currentUserId.value == null || user == null -> CockpitLogin(store)
                user.role == CockpitRole.ONBOARDING -> CockpitLocked(store, user)
                store.currentBranchId.value == null -> CockpitBranchSelect(store)
                else -> CockpitShell(store, user)
            }
        }
    }
}

@Composable
private fun CockpitLogin(store: CockpitStore) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(CockpitPadPage),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Text("FINANCE COCKPIT", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CockpitColors.Brass)
        Text("Coordinator-first ledger", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = CockpitColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick a profile to sign in. Fake data only — no network, no backend.",
            color = CockpitColors.Muted,
        )
        Spacer(Modifier.height(24.dp))
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(CockpitGap)) {
            for (person in store.users) {
                CockpitLedgerCard(
                    title = person.name,
                    subtitle = "${person.role.label} · home ${store.branches.firstOrNull { it.id == person.homeBranchId }?.name ?: "?"} · slot ${person.slot}",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CockpitChip(
                            person.role.label,
                            if (person.role == CockpitRole.ONBOARDING) CockpitColors.Slate else CockpitColors.Brass,
                        )
                        Spacer(Modifier.weight(1f))
                        CockpitPrimary("Sign in", onClick = {
                            store.currentUserId.value = person.id
                            store.audit(person.name, "LOGIN", "auth:${person.id}", "Signed in to finance-cockpit prototype.")
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        CockpitNote("Prototype finance-cockpit: every figure on screen is local fake data. Nothing leaves this window.")
    }
}

@Composable
private fun CockpitLocked(store: CockpitStore, user: CockpitUser) {
    Column(
        Modifier.fillMaxSize().padding(CockpitPadPage),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CockpitLedgerCard(title = "Onboarding hold", subtitle = user.name, accent = CockpitColors.Slate) {
            Text("ONBOARDING carries an empty capability bundle: no screens, no branches, no finance.", color = CockpitColors.Muted, fontSize = 14.sp)
            Text("A MANAGER must grant a real role before this account can proceed.", color = CockpitColors.Muted, fontSize = 14.sp)
            CockpitRowButtons {
                CockpitSecondary("Sign out", onClick = {
                    store.audit(user.name, "LOGOUT", "auth:${user.id}", "Signed out from locked onboarding screen.")
                    store.currentUserId.value = null
                })
            }
        }
    }
}

@Composable
private fun CockpitBranchSelect(store: CockpitStore) {
    val user = store.currentUser() ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(CockpitPadPage)) {
        Text("Select branch", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = CockpitColors.Ink)
        Text("Home branch opens fully. Any other branch opens as view-only relief duty until a grant lands.", color = CockpitColors.Muted)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(CockpitGap)) {
            for (branch in store.branches) {
                val home = branch.id == user.homeBranchId
                CockpitLedgerCard(
                    title = branch.name,
                    subtitle = "${branch.kind.label} · ${if (home) "home branch" else "relief duty (view-only until granted)"}",
                    accent = if (home) CockpitColors.Emerald else CockpitColors.Gold,
                ) {
                    CockpitRowButtons {
                        CockpitPrimary(if (home) "Open home" else "Clock in as relief", onClick = {
                            store.currentBranchId.value = branch.id
                            if (!store.clockedIn.contains(branch.id)) store.clockedIn.add(branch.id)
                            if (!home && !store.reliefEdit.contains(branch.id)) {
                                store.audit(user.name, "RELIEF_DUTY", "branch:${branch.id}", "Clocked in as relief duty (view-only).")
                            } else {
                                store.audit(user.name, "CLOCK_IN", "branch:${branch.id}", "Clocked in at ${branch.name}.")
                            }
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        CockpitQuiet("Back to login", onClick = { store.currentUserId.value = null })
    }
}

@Composable
private fun CockpitShell(store: CockpitStore, user: CockpitUser) {
    val branch = store.currentBranch() ?: return
    val screen = store.screen.value
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(208.dp).fillMaxHeight().background(CockpitColors.PineDeep).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("COCKPIT", color = CockpitColors.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(branch.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(user.name + " · " + user.role.label, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            for (entry in CockpitScreen.entries) {
                val selected = entry == screen
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) CockpitColors.Gold else Color.Transparent)
                        .clickable { store.screen.value = entry }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(
                        entry.label,
                        color = if (selected) CockpitColors.PineDeep else Color.White.copy(alpha = 0.85f),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Fake data · no network", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            CockpitPnLHero(store)
            CockpitDayBanner(store)
            Box(Modifier.weight(1f).fillMaxWidth().padding(CockpitPadPage)) {
                when (screen) {
                    CockpitScreen.HOME -> CockpitHome(store, user)
                    CockpitScreen.SESSIONS -> CockpitSessions(store, user)
                    CockpitScreen.CLIENTS -> CockpitClients(store)
                    CockpitScreen.FINANCE -> CockpitFinance(store, user)
                    CockpitScreen.TEAM -> CockpitTeam(store)
                    CockpitScreen.MAILBOX -> CockpitMailbox(store, user)
                    CockpitScreen.AUDIT -> CockpitAuditList(store)
                    CockpitScreen.PROFILE -> CockpitProfile(store, user)
                }
            }
        }
    }
}

@Composable
private fun CockpitPnLHero(store: CockpitStore) {
    val branchId = store.currentBranchId.value ?: return
    val date = store.selectedDay.value
    val sessionTotal = store.sessionIncome(branchId, date)
    val productTotal = store.productIncome()
    val grand = sessionTotal + productTotal
    Row(
        Modifier.fillMaxWidth().background(CockpitColors.Ink).padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("P&L TODAY · $date", color = CockpitColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(cockpitPeso(grand), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        }
        HeroCell("SESSION", cockpitPeso(sessionTotal))
        Spacer(Modifier.width(28.dp))
        HeroCell("PRODUCT", cockpitPeso(productTotal))
        Spacer(Modifier.width(28.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("SNAPSHOTS", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${store.remittances.count { it.state == CockpitSubmission.SUBMITTED }} frozen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun HeroCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun CockpitDayBanner(store: CockpitStore) {
    val branchId = store.currentBranchId.value ?: return
    val days = store.days.filter { it.branchId == branchId }
    Column(Modifier.fillMaxWidth().background(CockpitColors.PaperDeep).padding(horizontal = 28.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Branch day", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CockpitColors.Ink)
            Spacer(Modifier.width(12.dp))
            for (day in days) {
                val selected = day.date == store.selectedDay.value
                Box(Modifier.padding(end = 8.dp)) {
                    CockpitChip(
                        "${day.date} · ${day.state.label}",
                        cockpitToneFor(day.state),
                        onClick = { store.selectedDay.value = day.date },
                    )
                    if (selected) {
                        Text("  ◉", fontSize = 12.sp, color = CockpitColors.Ink)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            val state = store.dayState(branchId, store.selectedDay.value)
            CockpitChip(state.label, cockpitToneFor(state))
        }
        Text(
            "Operational-day boundary 04:00 Asia/Manila — OPEN stays editable until 04:00 next morning, then turns PAST lazily. REMITTED needs Coordinator edits with flagged audit.",
            fontSize = 12.sp,
            color = CockpitColors.Muted,
        )
    }
}

@Composable
private fun CockpitHome(store: CockpitStore, user: CockpitUser) {
    val branch = store.currentBranch() ?: return
    val branchId = branch.id
    val clocked = store.clockedIn.contains(branchId)
    val canEdit = branchId == user.homeBranchId || store.reliefEdit.contains(branchId)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CockpitGap)) {
        Text("Duty cockpit", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CockpitColors.Ink)
        CockpitLedgerCard(title = if (clocked) "Clocked in · ${branch.name}" else "Not clocked in", subtitle = if (canEdit) "Edit access" else "View-only relief duty", accent = CockpitColors.Emerald) {
            CockpitRowButtons {
                if (clocked) {
                    CockpitSecondary("Clock out", onClick = {
                        store.clockedIn.remove(branchId)
                        store.audit(user.name, "CLOCK_OUT", "branch:$branchId", "Clocked out of ${branch.name}.")
                    })
                } else {
                    CockpitPrimary("Clock in", onClick = {
                        store.clockedIn.add(branchId)
                        store.audit(user.name, "CLOCK_IN", "branch:$branchId", "Clocked in at ${branch.name}.")
                    })
                }
                CockpitQuiet("Switch branch", onClick = { store.currentBranchId.value = null })
            }
            if (!canEdit) {
                CockpitNote("Relief duty starts view-only. Ask for a relief grant (request below) or wait for a branch invite — edit unlocks when granted.")
            }
        }
        CockpitLedgerCard(title = "Relief requests", subtitle = "outsider-initiated · one live per requester per branch per date", accent = CockpitColors.Gold) {
            var targetDate by remember { mutableStateOf("2026-09-10") }
            TextField(value = targetDate, onValueChange = { targetDate = it }, label = { Text("Date YYYY-MM-DD") }, singleLine = true)
            Spacer(Modifier.height(4.dp))
            CockpitRowButtons {
                CockpitPrimary("Request relief here", onClick = {
                    store.requests.add(CockpitRequest("q${store.requests.size + 1}", user.id, branchId, targetDate, CockpitReliefState.LIVE))
                    store.audit(user.name, "RELIEF_REQUEST", "branch:$branchId", "Requested relief access for $targetDate.")
                })
            }
            for (req in store.requests.filter { it.branchId == branchId }) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${req.date} · ${req.state.label}", Modifier.weight(1f), fontSize = 13.sp)
                    if (req.state == CockpitReliefState.LIVE) {
                        CockpitQuiet("Simulate grant", onClick = {
                            val idx = store.requests.indexOf(req)
                            store.requests[idx] = req.copy(state = CockpitReliefState.GRANTED)
                            if (!store.reliefEdit.contains(branchId)) store.reliefEdit.add(branchId)
                            store.audit(user.name, "RELIEF_GRANT", "branch:$branchId", "Relief grant simulated for ${req.date}. Edit unlocked.")
                        })
                        CockpitQuiet("Withdraw", onClick = {
                            val idx = store.requests.indexOf(req)
                            store.requests[idx] = req.copy(state = CockpitReliefState.WITHDRAWN)
                            store.audit(user.name, "RELIEF_WITHDRAW", "branch:$branchId", "Withdrew relief request for ${req.date}.")
                        })
                    }
                }
            }
        }
        CockpitLedgerCard(title = "Relief invites", subtitle = "branch-initiated · accept to write the day grant", accent = CockpitColors.Pine) {
            val mine = store.invites.filter { it.toUserId == user.id && it.accepted == null }
            if (mine.isEmpty()) {
                Text("No pending invites for you.", color = CockpitColors.Muted, fontSize = 13.sp)
            }
            for (invite in mine) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${invite.date} · ${store.branches.firstOrNull { it.id == invite.branchId }?.name ?: invite.branchId}", Modifier.weight(1f), fontSize = 13.sp)
                    CockpitQuiet("Accept", onClick = {
                        val idx = store.invites.indexOf(invite)
                        store.invites[idx] = invite.copy(accepted = true)
                        if (!store.reliefEdit.contains(invite.branchId)) store.reliefEdit.add(invite.branchId)
                        store.audit(user.name, "INVITE_ACCEPT", "branch:${invite.branchId}", "Accepted relief invite for ${invite.date}.")
                    })
                    CockpitQuiet("Decline", onClick = {
                        val idx = store.invites.indexOf(invite)
                        store.invites[idx] = invite.copy(accepted = false)
                        store.audit(user.name, "INVITE_DECLINE", "branch:${invite.branchId}", "Declined relief invite for ${invite.date}.")
                    })
                }
            }
        }
    }
}

@Composable
private fun CockpitSessions(store: CockpitStore, user: CockpitUser) {
    var filter by remember { mutableStateOf<CockpitSessionStatus?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    val branchId = store.currentBranchId.value ?: return
    val date = store.selectedDay.value
    val roster = store.sessions.filter { it.branchId == branchId && it.dayDate == date }
    val visible = if (filter == null) roster else roster.filter { it.status == filter }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CockpitGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sessions · $date", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CockpitColors.Ink, modifier = Modifier.weight(1f))
            CockpitSecondary("New session", onClick = { showCreate = true })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CockpitChip("All", CockpitColors.Ink, onClick = { filter = null })
            for (status in CockpitSessionStatus.entries) {
                CockpitChip(status.label, cockpitToneFor(status), onClick = { filter = status })
            }
        }
        CockpitNote("Walk-in sessions cannot be marked NO_SHOW or CANCELLED. Global rule: a client holds at most one PENDING session.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(visible, key = { it.id }) { session ->
                CockpitLedgerCard(
                    title = "${session.time} · ${session.clientName}",
                    subtitle = "${session.type} · ${session.practitioners} · ${cockpitPeso(session.price)}${if (session.walkIn) " · walk-in" else ""}${if (session.voided) " · VOIDED" else ""}",
                    accent = cockpitToneFor(session.status),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CockpitChip(session.status.label, cockpitToneFor(session.status))
                        Spacer(Modifier.weight(1f))
                        CockpitQuiet("Open", onClick = { openId = session.id })
                    }
                }
            }
        }
    }
    val open = store.sessions.firstOrNull { it.id == openId }
    if (open != null) {
        var reason by remember(open.id) { mutableStateOf(open.voidReason ?: "") }
        AlertDialog(
            onDismissRequest = { openId = null },
            title = { Text("${open.time} · ${open.clientName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status: ${open.status.label} · ${open.type} · ${cockpitPeso(open.price)}", style = MaterialTheme.typography.bodyMedium)
                    if (open.walkIn) Text("Walk-in rule: NO_SHOW and CANCELLED are disabled for walk-ins.", color = CockpitColors.Danger)
                    if (open.voided) Text("Voided: ${open.voidReason ?: "no reason recorded"}")
                    val idx = store.sessions.indexOf(open)
                    Text("Move status:", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (next in CockpitSessionStatus.entries) {
                            val blocked = open.walkIn && (next == CockpitSessionStatus.NO_SHOW || next == CockpitSessionStatus.CANCELLED)
                            val btn: @Composable () -> Unit = {
                                CockpitQuiet(next.label, onClick = {
                                    if (!blocked) {
                                        store.sessions[idx] = open.copy(status = next)
                                        store.audit(user.name, "SESSION_STATUS", "session:${open.id}", "Moved to ${next.label}.")
                                        openId = null
                                    }
                                })
                            }
                            if (blocked) {
                                Text(next.label + " ✕", color = CockpitColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(6.dp))
                            } else {
                                btn()
                            }
                        }
                    }
                    TextField(value = reason, onValueChange = { reason = it }, label = { Text("Void reason") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!open.voided) {
                            CockpitQuiet("Void with reason", onClick = {
                                if (reason.isNotBlank()) {
                                    store.sessions[idx] = open.copy(voided = true, voidReason = reason)
                                    store.audit(user.name, "VOID", "session:${open.id}", "Voided: $reason")
                                    openId = null
                                }
                            })
                        } else {
                            CockpitQuiet("Unvoid", onClick = {
                                store.sessions[idx] = open.copy(voided = false, voidReason = null)
                                store.audit(user.name, "UNVOID", "session:${open.id}", "Unvoided (was: ${open.voidReason}).")
                                openId = null
                            })
                        }
                    }
                }
            },
            confirmButton = { CockpitQuiet("Close", onClick = { openId = null }) },
        )
    }
    if (showCreate) {
        var clientId by remember { mutableStateOf("c-4") }
        var time by remember { mutableStateOf("16:00") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(value = clientId, onValueChange = { clientId = it }, label = { Text("Client id (c-1..c-4)") }, singleLine = true)
                    TextField(value = time, onValueChange = { time = it }, label = { Text("Time HH:MM") }, singleLine = true)
                    val clash = store.pendingFor(clientId)
                    if (clash != null) Text("Blocked: ${clash.clientName} already holds PENDING ${clash.id}.", color = CockpitColors.Danger)
                }
            },
            confirmButton = {
                CockpitQuiet("Create PENDING", onClick = {
                    val clash = store.pendingFor(clientId)
                    val client = store.clients.firstOrNull { it.id == clientId }
                    if (clash == null && client != null) {
                        store.sessions.add(
                            CockpitSession("s${store.sessions.size + 1}", time, client.id, client.name, branchId, date, "Standard", CockpitSessionStatus.PENDING, 1000, user.name, false),
                        )
                        store.audit(user.name, "SESSION_CREATE", "client:$clientId", "Created PENDING session at $time.")
                        showCreate = false
                    }
                })
            },
            dismissButton = { CockpitQuiet("Cancel", onClick = { showCreate = false }) },
        )
    }
}

@Composable
private fun CockpitClients(store: CockpitStore) {
    var query by remember { mutableStateOf("") }
    val list = store.clients.filter { it.name.contains(query, ignoreCase = true) || query.isBlank() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CockpitGap)) {
        Text("Clients · global registry", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CockpitColors.Ink)
        CockpitNote("Clients are global across branches. At most one PENDING session per client. Anonymized records keep gender + age only.")
        TextField(value = query, onValueChange = { query = it }, label = { Text("Search clients") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(list, key = { it.id }) { client ->
                val pending = store.pendingFor(client.id)
                CockpitLedgerCard(
                    title = client.name,
                    subtitle = if (client.anonymized) "Anonymized · ${client.gender} · age ${client.age}" else "${client.gender} · age ${client.age} · ${client.phone}",
                ) {
                    if (pending != null) {
                        Text("Holds PENDING ${pending.id} — new session blocked.", color = CockpitColors.Danger, fontSize = 13.sp)
                    } else {
                        Text("No live PENDING — eligible for a new session.", color = CockpitColors.Emerald, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
