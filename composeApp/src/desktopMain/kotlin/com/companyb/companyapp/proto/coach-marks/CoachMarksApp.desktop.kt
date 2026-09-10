package com.companyb.companyapp.proto.coachmarks

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
fun CoachMarksApp() {
    val store = remember { seedCoachMarksStore() }
    logInfo("CoachMarks", "coach-marks prototype started")
    CoachMarksTheme {
        val userId = store.currentUserId.value
        val user = store.currentUser()
        Box(Modifier.fillMaxSize().background(CmInk)) {
            when {
                userId == null || user == null -> LoginScreen(store)
                user.role == CmRole.ONBOARDING -> LockedScreen(store, user)
                store.currentBranchId.value == null -> BranchSelectScreen(store)
                else -> MainShell(store, user)
            }
        }
    }
}

@Composable
private fun LoginScreen(store: CoachMarksStore) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Night desk", color = CmAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Sign in — coach-marks prototype", color = CmText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        CmHint("Fake users only. No network calls leave this window. Pick a profile to begin the guided tour.")
        store.users.forEach { user ->
            CmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, color = CmText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${user.role.label} · home: ${store.branches.firstOrNull { it.id == user.homeBranchId }?.name}",
                            color = CmMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Button(
                        onClick = {
                            store.currentUserId.value = user.id
                            store.clockedIn.value = false
                            store.reliefEdit.value = false
                            store.currentScreen.value = CmScreen.HOME
                            store.audit(user.name, "SIGN_IN", "users:${user.id}", "Signed in (fake).")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CmAmber, contentColor = CmAmberInk),
                    ) {
                        Text("Sign in", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedScreen(store: CoachMarksStore, user: CmUser) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Locked", color = CmBad, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Hi ${user.name}", color = CmText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        CmCard {
            CmSectionTitle("ONBOARDING — empty capability bundle")
            Spacer(Modifier.height(6.dp))
            CmHint(
                "Freshly registered users are locked out of every surface until MANAGE_USERS grants " +
                    "a real role. Nothing derives from an empty bundle — not even after a branch assignment.",
            )
        }
        OutlinedButton(onClick = {
            store.audit(user.name, "SIGN_OUT", "users:${user.id}", "Signed out from locked screen.")
            store.currentUserId.value = null
        }) {
            Text("Sign out")
        }
    }
}

@Composable
private fun BranchSelectScreen(store: CoachMarksStore) {
    val user = store.currentUser()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pick a branch", color = CmText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        CmHint("Your home branch opens fully. Any other branch opens as relief duty — view-only until edit access is granted.")
        store.branches.forEach { branch ->
            val isHome = branch.id == user?.homeBranchId
            CmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(branch.name, color = CmText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        CmChip(if (isHome) "Home branch" else "Relief duty · view-only", if (isHome) CmGood else CmWarn)
                    }
                    Button(
                        onClick = {
                            store.currentBranchId.value = branch.id
                            store.reliefEdit.value = isHome
                            store.currentScreen.value = CmScreen.HOME
                            store.audit(
                                user?.name ?: "?",
                                "BRANCH_OPEN",
                                "branches:${branch.id}",
                                if (isHome) "Opened home branch." else "Checked in as relief (view-only).",
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CmAmber, contentColor = CmAmberInk),
                    ) {
                        Text("Open", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        TextButton(onClick = { store.currentUserId.value = null }) {
            Text("Back to sign-in", color = CmMuted)
        }
    }
}

@Composable
private fun MainShell(store: CoachMarksStore, user: CmUser) {
    LaunchedEffect(Unit) {
        if (!store.tourEverStarted.value && !store.dontShowAgain.value) store.startTour()
    }
    val branch = store.currentBranch()
    val unread = store.notifications.count { !it.read }
    val (finished, total) = store.tourProgress()
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxHeight().width(208.dp).background(CmPanel).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("NIGHT DESK", color = CmAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(branch?.name ?: "", color = CmText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CmScreen.entries.forEach { screen ->
                val selected = store.currentScreen.value == screen
                val badge = when (screen) {
                    CmScreen.MAILBOX -> if (unread > 0) "$unread" else null
                    CmScreen.TOUR -> "$finished/$total"
                    else -> null
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { store.currentScreen.value = screen }
                        .background(if (selected) CmAmber.copy(alpha = 0.14f) else CmPanel)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        screen.label,
                        color = if (selected) CmAmber else CmText,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (badge != null) {
                        Text(badge, color = if (selected) CmAmber else CmMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(user.name, color = CmMuted, fontSize = 12.sp)
            Text(user.role.label, color = CmMuted, fontSize = 12.sp)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    store.currentScreen.value.label,
                    color = CmText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                CmChip(if (store.clockedIn.value) "Clocked in" else "Clocked out", if (store.clockedIn.value) CmGood else CmMuted)
            }
            TourBanner(store)
            BranchDayBanner(store)
            when (store.currentScreen.value) {
                CmScreen.HOME -> HomeScreen(store, user)
                CmScreen.SESSIONS -> SessionsScreen(store, user)
                CmScreen.CLIENTS -> ClientsScreen(store)
                CmScreen.FINANCE -> FinanceScreen(store, user)
                CmScreen.TEAM -> TeamScreen(store)
                CmScreen.MAILBOX -> MailboxScreen(store)
                CmScreen.AUDIT -> AuditScreen(store)
                CmScreen.PROFILE -> ProfileScreen(store, user)
                CmScreen.TOUR -> TourChecklistScreen(store)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    TourGuideDialog(store)
}

private fun isStepCurrent(store: CoachMarksStore, id: String): Boolean =
    store.tourActive.value && store.currentStep()?.id == id

@Composable
private fun BranchDayBanner(store: CoachMarksStore) {
    val branchId = store.currentBranchId.value ?: return
    val branch = store.currentBranch() ?: return
    val branchDays = store.days.filter { it.branchId == branchId }
    val current = store.branchDay(branchId, store.homeDayFilter.value)
    CmCard(
        highlighted = isStepCurrent(store, "branchday"),
        beaconNumber = store.stepNumber("branchday"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(branch.name, color = CmText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Branch day ${store.homeDayFilter.value}", color = CmMuted, fontSize = 12.sp)
            }
            if (current != null) CmChip(current.state.label, dayColor(current.state))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            branchDays.forEach { day ->
                val selected = day.date == store.homeDayFilter.value
                OutlinedButton(onClick = {
                    store.homeDayFilter.value = day.date
                    store.maybeAdvance("branchday")
                    store.audit(
                        store.currentUser()?.name ?: "?",
                        "DAY_VIEW",
                        "branch-days:${day.date}",
                        "Viewing ${day.state.label} day.",
                    )
                }) {
                    Text(
                        "${day.date.takeLast(5)} · ${day.state.label}",
                        color = if (selected) CmAmber else CmMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        CmHint("Day boundary 04:00 Asia/Manila — OPEN days edit freely, PAST is coordinator-only, REMITTED is frozen.")
    }
}

@Composable
private fun HomeScreen(store: CoachMarksStore, user: CmUser) {
    val branch = store.currentBranch()
    val isHome = branch?.id == user.homeBranchId
    CmCard(
        highlighted = isStepCurrent(store, "welcome"),
        beaconNumber = store.stepNumber("welcome"),
    ) {
        CmSectionTitle("Welcome, ${user.name}")
        Spacer(Modifier.height(4.dp))
        CmHint(
            "Your home branch is ${store.branches.firstOrNull { it.id == user.homeBranchId }?.name}. " +
                "This card, the banner and the rail cover the whole day — the guide beacons point at each region in turn.",
        )
    }
    CmCard(
        highlighted = isStepCurrent(store, "clockin"),
        beaconNumber = store.stepNumber("clockin"),
    ) {
        CmSectionTitle("Clock-in")
        Spacer(Modifier.height(4.dp))
        CmHint(
            if (isHome) "Home branch — clocking in grants full access."
            else "Relief duty at ${branch?.name} — view-only until edit access is granted.",
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!store.clockedIn.value) {
                Button(
                    onClick = {
                        store.clockedIn.value = true
                        if (isHome && !store.commissionIncluded.contains(user.name)) {
                            store.commissionIncluded.add(user.name)
                        }
                        store.audit(user.name, "CLOCK_IN", "attendance:${branch?.id}", "Clocked in.")
                        store.maybeAdvance("clockin")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CmAmber, contentColor = CmAmberInk),
                ) {
                    Text("Clock in", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = {
                    store.clockedIn.value = false
                    store.audit(user.name, "CLOCK_OUT", "attendance:${branch?.id}", "Clocked out.")
                }) {
                    Text("Clock out")
                }
            }
            if (!isHome) CmChip(if (store.reliefEdit.value) "Edit access" else "View-only", if (store.reliefEdit.value) CmGood else CmWarn)
        }
        if (!isHome && !store.reliefEdit.value) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val id = "r-${100 + store.relief.size}"
                    store.relief.add(CmRelief(id, CmReliefKind.REQUEST, user.name, branch?.id ?: "", store.homeDayFilter.value, "Pending branch grant"))
                    store.audit(user.name, "RELIEF_REQUEST", "relief:$id", "Requested relief edit access.")
                    store.notify("Relief request sent", "${user.name} asked for edit access at ${branch?.name}.", branch?.id, store.homeDayFilter.value)
                }) {
                    Text("Request edit access", fontSize = 12.sp)
                }
                OutlinedButton(onClick = {
                    store.reliefEdit.value = true
                    store.audit(user.name, "RELIEF_GRANT", "relief:simulated", "Branch grant simulated — edit access on.")
                    store.maybeAdvance("clockin")
                }) {
                    Text("Simulate branch grant", fontSize = 12.sp)
                }
            }
        }
    }
    CmCard {
        CmSectionTitle("Relief board")
        Spacer(Modifier.height(8.dp))
        if (store.relief.isEmpty()) CmHint("No relief activity today.")
        store.relief.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${item.person} · ${item.kind.label}", color = CmText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${store.branches.firstOrNull { it.id == item.branchId }?.name} · ${item.dayDate} · ${item.state}",
                        color = CmMuted,
                        fontSize = 12.sp,
                    )
                }
                if (item.kind == CmReliefKind.INVITE && item.state == "Invite sent") {
                    TextButton(onClick = {
                        val idx = store.relief.indexOfFirst { it.id == item.id }
                        if (idx >= 0) store.relief[idx] = item.copy(state = "Accepted — grant written")
                        store.audit(user.name, "RELIEF_ACCEPT", "relief:${item.id}", "Accepted relief invite.")
                    }) {
                        Text("Accept", color = CmGood, fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        val idx = store.relief.indexOfFirst { it.id == item.id }
                        if (idx >= 0) store.relief[idx] = item.copy(state = "Declined")
                        store.audit(user.name, "RELIEF_DECLINE", "relief:${item.id}", "Declined relief invite.")
                    }) {
                        Text("Decline", color = CmBad, fontSize = 12.sp)
                    }
                }
                if (item.kind == CmReliefKind.REQUEST && item.person == user.name && item.state == "Pending branch grant") {
                    TextButton(onClick = {
                        val idx = store.relief.indexOfFirst { it.id == item.id }
                        if (idx >= 0) store.relief[idx] = item.copy(state = "Withdrawn")
                        store.audit(user.name, "RELIEF_WITHDRAW", "relief:${item.id}", "Withdrew own relief request.")
                    }) {
                        Text("Withdraw", color = CmMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionsScreen(store: CoachMarksStore, user: CmUser) {
    var filter by remember { mutableStateOf<CmSessionStatus?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    val branchId = store.currentBranchId.value ?: return
    val daySessions = store.sessions.filter { it.branchId == branchId && it.dayDate == store.homeDayFilter.value }
    val visible = if (filter == null) daySessions else daySessions.filter { it.status == filter }
    CmCard(
        highlighted = isStepCurrent(store, "sessions") || isStepCurrent(store, "voidrule"),
        beaconNumber = store.stepNumber(if (isStepCurrent(store, "voidrule")) "voidrule" else "sessions"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CmSectionTitle("Sessions")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showCreate = true }) {
                Text("+ New session", color = CmAmber, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { filter = null }) {
                Text("All", color = if (filter == null) CmAmber else CmMuted, fontWeight = FontWeight.Bold)
            }
            CmSessionStatus.entries.forEach { status ->
                TextButton(onClick = { filter = status }) {
                    Text(
                        status.label,
                        color = if (filter == status) statusColor(status) else CmMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (visible.isEmpty()) CmHint("No sessions on this day yet — create one above.")
        visible.forEach { session ->
            Row(
                Modifier.fillMaxWidth().clickable { detailId = session.id }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${session.time} · ${session.clientName}${if (session.voided) " · VOID" else ""}",
                        color = CmText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${session.id} · ${session.type} · ${peso(session.price)}${if (session.walkIn) " · walk-in" else ""}",
                        color = CmMuted,
                        fontSize = 12.sp,
                    )
                }
                CmChip(session.status.label, statusColor(session.status))
            }
        }
        Spacer(Modifier.height(6.dp))
        CmRuleNote("Walk-in sessions can never be NO_SHOW or CANCELLED — those buttons stay disabled with this rule attached.")
    }
    val detail = store.sessions.firstOrNull { it.id == detailId }
    if (detail != null) {
        SessionDetailDialog(store, user, detail, onClose = { detailId = null })
    }
    if (showCreate) {
        SessionCreateDialog(store, user, onClose = { showCreate = false })
    }
}

@Composable
private fun SessionDetailDialog(store: CoachMarksStore, user: CmUser, session: CmSession, onClose: () -> Unit) {
    var reason by remember { mutableStateOf(session.voidReason ?: "") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${session.id} · ${session.clientName}", color = CmText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CmChip(session.status.label, statusColor(session.status))
                    if (session.walkIn) CmChip("Walk-in", CmInfo)
                    if (session.voided) CmChip("Void", CmBad)
                }
                CmHint("${session.type} · ${session.time} · ${peso(session.price)} · ${session.practitioners}")
                if (session.status == CmSessionStatus.PENDING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            updateSession(store, user, session, CmSessionStatus.COMPLETED)
                            onClose()
                        }) {
                            Text("Complete", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                updateSession(store, user, session, CmSessionStatus.NO_SHOW)
                                onClose()
                            },
                            enabled = !session.walkIn,
                        ) {
                            Text("No-show", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                updateSession(store, user, session, CmSessionStatus.CANCELLED)
                                onClose()
                            },
                            enabled = !session.walkIn,
                        ) {
                            Text("Cancel", fontSize = 12.sp)
                        }
                    }
                    if (session.walkIn) {
                        CmHint("No-show and Cancelled stay disabled: walk-in sessions cannot be marked either way.")
                    }
                } else {
                    OutlinedButton(onClick = {
                        updateSession(store, user, session, CmSessionStatus.PENDING)
                        onClose()
                    }) {
                        Text("Reopen to Pending", fontSize = 12.sp)
                    }
                }
                TextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Void reason (required to void)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!session.voided) {
                        OutlinedButton(
                            onClick = {
                                if (reason.isNotBlank()) {
                                    val idx = store.sessions.indexOfFirst { it.id == session.id }
                                    if (idx >= 0) store.sessions[idx] = session.copy(voided = true, voidReason = reason)
                                    store.audit(user.name, "VOID", "sessions:${session.id}", "Voided: $reason")
                                    store.maybeAdvance("voidrule")
                                    onClose()
                                }
                            },
                            enabled = reason.isNotBlank(),
                        ) {
                            Text("Void with reason", fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(onClick = {
                            val idx = store.sessions.indexOfFirst { it.id == session.id }
                            if (idx >= 0) store.sessions[idx] = session.copy(voided = false, voidReason = null)
                            store.audit(user.name, "UNVOID", "sessions:${session.id}", "Void lifted.")
                            onClose()
                        }) {
                            Text("Unvoid", fontSize = 12.sp)
                        }
                    }
                }
                if (session.voided) CmHint("Voided sessions stay visible but are excluded from totals.")
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close", color = CmAmber)
            }
        },
        containerColor = CmPanel2,
    )
}

private fun updateSession(store: CoachMarksStore, user: CmUser, session: CmSession, next: CmSessionStatus) {
    val idx = store.sessions.indexOfFirst { it.id == session.id }
    if (idx >= 0) store.sessions[idx] = session.copy(status = next)
    val clientIdx = store.clients.indexOfFirst { it.id == session.clientId }
    if (clientIdx >= 0 && next != CmSessionStatus.PENDING) {
        val client = store.clients[clientIdx]
        if (client.pendingSessionId == session.id) store.clients[clientIdx] = client.copy(pendingSessionId = null)
    }
    store.audit(user.name, "UPDATE", "sessions:${session.id}", "${session.status.label} -> ${next.label}.")
    if (next != CmSessionStatus.PENDING) store.maybeAdvance("sessions")
}

@Composable
private fun SessionCreateDialog(store: CoachMarksStore, user: CmUser, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var pickedId by remember { mutableStateOf<String?>(null) }
    val picked = store.clients.firstOrNull { it.id == pickedId }
    val blocked = picked?.pendingSessionId != null
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("New session", color = CmText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Client name (or pick below)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CmHint("Existing clients — one PENDING session at most:")
                store.clients.take(5).forEach { client ->
                    Row(
                        Modifier.fillMaxWidth().clickable { pickedId = client.id }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${if (pickedId == client.id) "● " else "○ "}${client.name}",
                            color = if (pickedId == client.id) CmAmber else CmText,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (client.pendingSessionId != null) CmChip("Has PENDING", CmWarn)
                    }
                }
                if (blocked) {
                    CmRuleNote("${picked?.name} already holds PENDING ${picked?.pendingSessionId} — booking is blocked until it closes.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val id = "S-${110 + store.sessions.size}"
                    val clientName = picked?.name ?: name.ifBlank { "New client" }
                    val branchId = store.currentBranchId.value ?: return@Button
                    store.sessions.add(
                        CmSession(
                            id = id, time = "15:00", clientId = picked?.id ?: "c-new",
                            clientName = clientName, branchId = branchId, dayDate = store.homeDayFilter.value,
                            type = "Follow-up", status = CmSessionStatus.PENDING, price = 1200,
                            practitioners = user.name, walkIn = false,
                        ),
                    )
                    if (picked != null) {
                        val idx = store.clients.indexOfFirst { it.id == picked.id }
                        if (idx >= 0) store.clients[idx] = picked.copy(pendingSessionId = id)
                    }
                    store.audit(user.name, "INSERT", "sessions:$id", "Session created for $clientName.")
                    onClose()
                },
                enabled = !blocked && (picked != null || name.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = CmAmber, contentColor = CmAmberInk),
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel", color = CmMuted)
            }
        },
        containerColor = CmPanel2,
    )
}

@Composable
private fun ClientsScreen(store: CoachMarksStore) {
    var query by remember { mutableStateOf("") }
    var openedId by remember { mutableStateOf<String?>(null) }
    val matches = store.clients.filter { it.name.contains(query, ignoreCase = true) }
    CmCard(
        highlighted = isStepCurrent(store, "clients"),
        beaconNumber = store.stepNumber("clients"),
    ) {
        CmSectionTitle("Clients — global records")
        Spacer(Modifier.height(4.dp))
        CmHint("Shared across all branches. At most one PENDING session per client.")
        Spacer(Modifier.height(8.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search clients") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        matches.forEach { client ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    openedId = client.id
                    store.maybeAdvance("clients")
                }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(client.name, color = CmText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (client.anonymized) "Age ${client.age} · ${client.gender} · PII withheld" else "Age ${client.age} · ${client.gender} · ${client.phone}",
                        color = CmMuted,
                        fontSize = 12.sp,
                    )
                }
                if (client.anonymized) CmChip("Anonymized", CmInfo)
                if (client.pendingSessionId != null) CmChip("PENDING", CmWarn)
            }
        }
        if (matches.isEmpty()) CmHint("No clients match that search.")
    }
    val opened = store.clients.firstOrNull { it.id == openedId }
    if (opened != null) {
        AlertDialog(
            onDismissRequest = { openedId = null },
            title = { Text(opened.name, color = CmText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (opened.anonymized) {
                        CmRuleNote("Anonymized view: gender and age are kept for reporting; all other PII is withheld.")
                    } else {
                        CmHint("Age ${opened.age} · ${opened.gender} · ${opened.phone}")
                    }
                    CmHint("Pending session: ${opened.pendingSessionId ?: "none"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { openedId = null }) {
                    Text("Close", color = CmAmber)
                }
            },
            containerColor = CmPanel2,
        )
    }
}

@Composable
private fun FinanceScreen(store: CoachMarksStore, user: CmUser) {
    var undoReason by remember { mutableStateOf("") }
    var undoTarget by remember { mutableStateOf<String?>(null) }
    val branchId = store.currentBranchId.value ?: return
    val pool = store.productLines.sumOf { it.total }
    val included = store.commissionIncluded.toList()
    val perHead = if (included.isNotEmpty()) pool / included.size else 0
    CmCard(
        highlighted = isStepCurrent(store, "finance"),
        beaconNumber = store.stepNumber("finance"),
    ) {
        CmSectionTitle("Remittance — draft, submit, snapshot, undo")
        Spacer(Modifier.height(4.dp))
        CmHint("SESSION and PRODUCT flows submit independently. Submitting freezes an immutable snapshot; undo needs a reason and works within 48 hours.")
        Spacer(Modifier.height(8.dp))
        store.remittances.filter { it.branchId == branchId }.forEach { remit ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${remit.id} · ${remit.kind.label} · ${remit.dayDate}",
                            color = CmText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Gross ${peso(remit.gross)} − costs ${peso(remit.deductions)} = net ${peso(remit.net)}",
                            color = CmMuted,
                            fontSize = 12.sp,
                        )
                        if (remit.snapshotId != null) CmHint("Snapshot ${remit.snapshotId} · ${remit.submittedAt}")
                        if (remit.undoReason != null) CmHint("Undone: ${remit.undoReason}")
                    }
                    CmChip(remit.state.label, if (remit.state == CmSubmission.DRAFT) CmWarn else CmGood)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (remit.state == CmSubmission.DRAFT) {
                        OutlinedButton(onClick = {
                            val idx = store.remittances.indexOfFirst { it.id == remit.id }
                            if (idx >= 0) {
                                store.remittances[idx] = remit.copy(
                                    state = CmSubmission.SUBMITTED,
                                    snapshotId = "SNAP-${8800 + idx * 7 + store.remittances.size}",
                                    submittedAt = "2026-09-09 21:40 Asia/Manila",
                                    undoReason = null,
                                )
                            }
                            store.audit(user.name, "SUBMIT", "remittances:${remit.id}", "Submitted — snapshot frozen.")
                            store.notify("Remittance submitted", "${remit.id} ${remit.kind.label.lowercase()} submitted.", branchId, remit.dayDate)
                            store.maybeAdvance("finance")
                        }) {
                            Text("Submit", fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(onClick = { undoTarget = remit.id }) {
                            Text("Undo (48h)", fontSize = 12.sp)
                        }
                    }
                }
                if (undoTarget == remit.id) {
                    TextField(
                        value = undoReason,
                        onValueChange = { undoReason = it },
                        label = { Text("Undo reason (required)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (undoReason.isNotBlank()) {
                                    val idx = store.remittances.indexOfFirst { it.id == remit.id }
                                    if (idx >= 0) {
                                        store.remittances[idx] = remit.copy(
                                            state = CmSubmission.DRAFT,
                                            snapshotId = null,
                                            submittedAt = null,
                                            undoReason = undoReason,
                                        )
                                    }
                                    store.audit(user.name, "UNDO", "remittances:${remit.id}", "Undone within 48h: $undoReason")
                                    undoReason = ""
                                    undoTarget = null
                                    store.maybeAdvance("finance")
                                }
                            },
                            enabled = undoReason.isNotBlank(),
                        ) {
                            Text("Confirm undo", fontSize = 12.sp)
                        }
                        TextButton(onClick = { undoTarget = null }) {
                            Text("Cancel", color = CmMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    CmCard {
        CmSectionTitle("Product pool + quantity")
        Spacer(Modifier.height(8.dp))
        store.productLines.forEach { line ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(line.name, color = CmText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${peso(line.unitPrice)} each · ${peso(line.total)}", color = CmMuted, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    val idx = store.productLines.indexOfFirst { it.id == line.id }
                    if (idx >= 0 && line.qty > 0) store.productLines[idx] = line.copy(qty = line.qty - 1)
                }) {
                    Text("−", color = CmAmber, fontWeight = FontWeight.Bold)
                }
                Text("${line.qty}", color = CmText, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    val idx = store.productLines.indexOfFirst { it.id == line.id }
                    if (idx >= 0) store.productLines[idx] = line.copy(qty = line.qty + 1)
                }) {
                    Text("+", color = CmAmber, fontWeight = FontWeight.Bold)
                }
            }
        }
        CmHint("Pool total ${peso(pool)} moves with the steppers.")
    }
    CmCard(
        highlighted = isStepCurrent(store, "commission"),
        beaconNumber = store.stepNumber("commission"),
    ) {
        CmSectionTitle("Commission split")
        Spacer(Modifier.height(4.dp))
        CmHint("Pooled per branch day, split equally across everyone clocked in at sale time — ${peso(perHead)} each right now. Overrides are audited.")
        Spacer(Modifier.height(8.dp))
        listOf("Dra. Amara Santos", "Maria Cruz", "Liam Tan").forEach { name ->
            val on = name in store.commissionIncluded
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (on) store.commissionIncluded.remove(name) else store.commissionIncluded.add(name)
                    store.audit(user.name, "COMMISSION_OVERRIDE", "commission:$name", if (on) "Excluded from split." else "Included in split.")
                    store.maybeAdvance("commission")
                }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = on,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = CmAmber, checkmarkColor = CmAmberInk),
                )
                Spacer(Modifier.width(8.dp))
                Text(name, color = CmText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(if (on) peso(perHead) else "out", color = if (on) CmGood else CmMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TeamScreen(store: CoachMarksStore) {
    LaunchedEffect(Unit) {
        if (store.tourActive.value && store.currentStep()?.screen == CmScreen.TOUR) {
            store.maybeAdvance("finish")
        }
    }
    CmCard(
        highlighted = isStepCurrent(store, "finish"),
        beaconNumber = store.stepNumber("finish"),
    ) {
        CmSectionTitle("Team — users and roles")
        Spacer(Modifier.height(4.dp))
        CmHint("Slots order reports (1 = senior); relief practitioners sort after home slots.")
        Spacer(Modifier.height(8.dp))
        store.users.forEach { member ->
            Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, color = CmText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    CmChip(member.role.label, if (member.role == CmRole.ONBOARDING) CmBad else CmInfo)
                }
                Text(
                    "Home ${store.branches.firstOrNull { it.id == member.homeBranchId }?.name} · slot ${member.slot}",
                    color = CmMuted,
                    fontSize = 12.sp,
                )
                Text(member.role.summary, color = CmMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MailboxScreen(store: CoachMarksStore) {
    CmCard(
        highlighted = isStepCurrent(store, "mailbox"),
        beaconNumber = store.stepNumber("mailbox"),
    ) {
        CmSectionTitle("Mailbox")
        Spacer(Modifier.height(4.dp))
        CmHint("Relief messages name the branch and day and tap through to that branch day. Read rows are kept forever.")
        Spacer(Modifier.height(8.dp))
        if (store.notifications.isEmpty()) CmHint("Mailbox empty.")
        store.notifications.forEach { note ->
            val idx = store.notifications.indexOfFirst { it.id == note.id }
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (idx >= 0) store.notifications[idx] = note.copy(read = true)
                    if (!note.read) store.maybeAdvance("mailbox")
                }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (note.read) "○" else "●", color = if (note.read) CmMuted else CmAmber, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(note.title, color = CmText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(note.body, color = CmMuted, fontSize = 12.sp)
                }
                if (note.branchId != null) {
                    TextButton(onClick = {
                        store.currentBranchId.value = note.branchId
                        if (note.dayDate != null) store.homeDayFilter.value = note.dayDate
                        store.currentScreen.value = CmScreen.HOME
                        store.audit(store.currentUser()?.name ?: "?", "NAVIGATE", "notifications:${note.id}", "Tapped through to branch day.")
                    }) {
                        Text("Open day", color = CmAmber, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditScreen(store: CoachMarksStore) {
    CmCard {
        CmSectionTitle("Audit log")
        Spacer(Modifier.height(4.dp))
        CmHint("Immutable record of every mutation — who changed what, when, and why. Newest first.")
        Spacer(Modifier.height(8.dp))
        store.audit.forEach { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("${entry.time} · ${entry.actor} · ${entry.action}", color = CmText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${entry.target} — ${entry.detail}", color = CmMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProfileScreen(store: CoachMarksStore, user: CmUser) {
    CmCard {
        CmSectionTitle("Profile")
        Spacer(Modifier.height(4.dp))
        CmHint("${user.name} · ${user.role.label}")
        CmHint(user.role.summary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (store.clockedIn.value) {
                OutlinedButton(onClick = {
                    store.clockedIn.value = false
                    store.audit(user.name, "CLOCK_OUT", "attendance:${store.currentBranchId.value}", "Clocked out from profile.")
                }) {
                    Text("Clock out")
                }
            }
            OutlinedButton(onClick = {
                store.audit(user.name, "SIGN_OUT", "users:${user.id}", "Signed out.")
                store.currentUserId.value = null
            }) {
                Text("Sign out")
            }
        }
    }
    CmCard {
        CmSectionTitle("Guided tour controls")
        Spacer(Modifier.height(4.dp))
        val (finished, total) = store.tourProgress()
        CmHint("Progress $finished of $total — ending early keeps everything usable.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (store.tourActive.value) {
                OutlinedButton(onClick = { store.endTour(completed = false) }) {
                    Text("End tour")
                }
            } else {
                Button(
                    onClick = { store.resumeTour() },
                    colors = ButtonDefaults.buttonColors(containerColor = CmAmber, contentColor = CmAmberInk),
                ) {
                    Text("Resume tour", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { store.restartTour() }) {
                    Text("Restart tour")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = store.dontShowAgain.value,
                onCheckedChange = { store.dontShowAgain.value = it },
                colors = CheckboxDefaults.colors(checkedColor = CmAmber, checkmarkColor = CmAmberInk),
            )
            Text("Don't auto-start the tour on sign-in", color = CmMuted, fontSize = 13.sp)
        }
    }
}
