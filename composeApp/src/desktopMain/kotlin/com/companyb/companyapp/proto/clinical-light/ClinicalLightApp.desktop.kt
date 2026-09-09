package com.companyb.companyapp.proto.clinicallight

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

private enum class ClinicalScreen(
    val label: String,
) {
    HOME("Home"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit log"),
    PROFILE("Profile"),
}

private fun peso(amount: Int): String {
    val digits = amount.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₱$digits"
}

@Composable
fun ClinicalLightApp() {
    val store = remember { seedClinicalLightStore() }
    logInfo("ClinicalLight", "clinical-light prototype started")
    ClinicalLightTheme {
        val userId = store.currentUserId.value
        val user = store.currentUser()
        Box(Modifier.fillMaxSize().background(Color.White)) {
            when {
                userId == null || user == null -> LoginScreen(store)
                user.role == FakeRole.ONBOARDING -> LockedScreen(store, user)
                store.currentBranchId.value == null -> BranchSelectScreen(store)
                else -> MainShell(store, user)
            }
        }
    }
}

// ---------- Auth ----------

@Composable
private fun LoginScreen(store: ClinicalLightStore) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ClinicalPadPage),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Calm Clinic", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "A quiet start to the clinic day. Choose a profile to sign in — fake data only.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(ClinicalGap)) {
            for (person in store.users) {
                SectionCard(title = person.name, subtitle = "${person.role.label} · Slot ${person.slot}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(
                            person.role.label,
                            if (person.role == FakeRole.ONBOARDING) ChipTone.GREY else ChipTone.BLUE,
                        )
                        Spacer(Modifier.weight(1f))
                        PrimaryAction("Sign in", onClick = {
                            store.currentUserId.value = person.id
                            store.audit(person.name, "LOGIN", "auth:${person.id}", "Signed in to clinical-light prototype.")
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        NoteCard("Prototype build clinical-light: no network calls, no backend. Everything on this screen is local fake data.")
    }
}

@Composable
private fun LockedScreen(store: ClinicalLightStore, user: FakeUser) {
    Column(
        Modifier.fillMaxSize().padding(ClinicalPadPage),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusChip("Onboarding", ChipTone.GREY)
        Spacer(Modifier.height(20.dp))
        Text("Welcome, ${user.name}.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your account is locked: the Onboarding role carries an empty capability bundle, " +
                "so nothing derives even with a branch assignment. Ask a Manager with MANAGE_USERS " +
                "to grant you a real role.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(560.dp),
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = {
            store.currentUserId.value = null
            store.audit(user.name, "LOGOUT", "auth:${user.id}", "Locked onboarding profile signed out.")
        }) {
            Text("Back to sign in")
        }
    }
}

@Composable
private fun BranchSelectScreen(store: ClinicalLightStore) {
    val user = store.currentUser()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ClinicalPadPage)) {
        Text("Choose your branch, ${user?.name?.substringBefore(" ") ?: "friend"}.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Home branches open fully. Any other branch starts as relief duty with view-only access.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(ClinicalGap), modifier = Modifier.width(720.dp)) {
            for (branch in store.branches) {
                val isHome = branch.id == user?.homeBranchId
                SectionCard(
                    title = branch.name,
                    subtitle = branch.kind.label + if (isHome) " · Home branch" else " · Relief duty",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(if (isHome) "Home" else "Relief", if (isHome) ChipTone.GREEN else ChipTone.BLUE)
                        Spacer(Modifier.weight(1f))
                        PrimaryAction("Open branch", onClick = {
                            store.currentBranchId.value = branch.id
                            store.reliefEdit.value = isHome
                            val name = user?.name ?: "Someone"
                            store.audit(name, "BRANCH_OPEN", "branches:${branch.id}", "Opened ${branch.name}.")
                        })
                    }
                }
            }
        }
    }
}

// ---------- Shell ----------

@Composable
private fun MainShell(store: ClinicalLightStore, user: FakeUser) {
    var screen by rememberSaveable { mutableStateOf(ClinicalScreen.HOME) }
    Row(Modifier.fillMaxSize()) {
        Rail(store, user, screen, onPick = { screen = it })
        Column(Modifier.weight(1f).fillMaxHeight()) {
            DayBanner(store)
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ClinicalPadPage)) {
                when (screen) {
                    ClinicalScreen.HOME -> HomeScreen(store, user)
                    ClinicalScreen.SESSIONS -> SessionsScreen(store, user)
                    ClinicalScreen.CLIENTS -> ClientsScreen(store, user)
                    ClinicalScreen.FINANCE -> FinanceScreen(store, user)
                    ClinicalScreen.TEAM -> TeamScreen(store)
                    ClinicalScreen.MAILBOX -> MailboxScreen(store, onOpenDay = { screen = ClinicalScreen.HOME })
                    ClinicalScreen.AUDIT -> AuditScreen(store)
                    ClinicalScreen.PROFILE -> ProfileScreen(store, user)
                }
            }
        }
    }
}

@Composable
private fun Rail(store: ClinicalLightStore, user: FakeUser, current: ClinicalScreen, onPick: (ClinicalScreen) -> Unit) {
    val unread = store.notifications.count { !it.read }
    Column(
        Modifier.width(240.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp),
    ) {
        Text("Calm Clinic", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(user.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(user.role.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        for (entry in ClinicalScreen.entries) {
            val selected = entry == current
            val label = if (entry == ClinicalScreen.MAILBOX && unread > 0) "${entry.label} ($unread)" else entry.label
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .border(1.dp, if (selected) MaterialTheme.colorScheme.outline else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onPick(entry) }.padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Text(
                    label,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        StatusChip(
            if (store.clockedIn.value) "Clocked in" else "Clocked out",
            if (store.clockedIn.value) ChipTone.GREEN else ChipTone.GREY,
        )
    }
}

@Composable
private fun DayBanner(store: ClinicalLightStore) {
    val branch = store.currentBranch() ?: return
    val date = store.homeDayFilter.value
    val day = store.branchDay(branch.id, date)
    val branchDays = store.days.filter { it.branchId == branch.id }
    Box(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = ClinicalPadPage, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(branch.name, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    StatusChip(day?.state?.label ?: "No day", day?.let { dayTone(it.state) } ?: ChipTone.GREY)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Branch day $date · boundary 04:00 Asia/Manila — the day stays editable until 04:00 the next morning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (option in branchDays) {
                val selected = option.date == date
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color.White else Color.Transparent)
                        .clickable { store.homeDayFilter.value = option.date }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        option.date.takeLast(5),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

// ---------- Home ----------

@Composable
private fun HomeScreen(store: ClinicalLightStore, user: FakeUser) {
    val branch = store.currentBranch()
    val date = store.homeDayFilter.value
    val isHome = branch?.id == user.homeBranchId
    val todaySessions = store.sessions.filter { it.branchId == branch?.id && it.dayDate == date }
    val pending = todaySessions.count { it.status == FakeSessionStatus.PENDING }
    val done = todaySessions.count { it.status == FakeSessionStatus.COMPLETED }

    Text("Good morning, ${user.name.substringBefore(" ")}.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        if (isHome) "Your home branch is ready for the day." else "You are on relief duty — view-only until the branch grants edit access.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(ClinicalGap)) {
        SectionCard(
            title = "Today at ${branch?.name ?: "—"}",
            subtitle = "$done completed · $pending pending · ${todaySessions.size} total",
            modifier = Modifier.weight(1f),
        ) {
            KeyValue("Completed income (non-voided)", peso(todaySessions.filter {
                it.status == FakeSessionStatus.COMPLETED && !it.voided
            }.sumOf { it.price }))
            Spacer(Modifier.height(6.dp))
            KeyValue("No-show / cancelled", "${todaySessions.count {
                it.status == FakeSessionStatus.NO_SHOW || it.status == FakeSessionStatus.CANCELLED
            }} sessions")
            Spacer(Modifier.height(6.dp))
            KeyValue("Voided (kept for record)", "${todaySessions.count { it.voided }} sessions")
        }
        SectionCard(
            title = if (store.clockedIn.value) "You are clocked in" else "Start your shift",
            subtitle = if (store.clockedIn.value) "Drawer: ${branch?.name}" else "Clock in to begin seeing clients",
            modifier = Modifier.weight(1f),
        ) {
            if (store.clockedIn.value) {
                StatusChip("On duty", ChipTone.GREEN)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    store.clockedIn.value = false
                    store.audit(user.name, "CLOCK_OUT", "attendance:${branch?.id}", "Clocked out at ${branch?.name}.")
                }) { Text("Clock out") }
            } else {
                PrimaryAction("Clock in at ${branch?.name ?: "branch"}", onClick = {
                    store.clockedIn.value = true
                    store.audit(user.name, "CLOCK_IN", "attendance:${branch?.id}", "Clocked in at ${branch?.name}.")
                    store.notify("Clock-in confirmed", "${user.name} clocked in at ${branch?.name} for $date.")
                })
            }
        }
    }
    Spacer(Modifier.height(ClinicalGap))

    SectionCard(
        title = "Relief duty, requests and invites",
        subtitle = "Relief starts view-only · edit needs a branch grant · expires 04:00 Manila next day",
    ) {
        if (!isHome && !store.reliefEdit.value) {
            NoteCard("You are viewing ${branch?.name} as relief with view-only access. Request edit access below.")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryAction("Request edit access", onClick = {
                    store.relief.add(
                        FakeRelief("r-new-${store.relief.size}", FakeReliefKind.REQUEST, user.name, branch?.id ?: "", date, "Pending branch grant"),
                    )
                    store.audit(user.name, "RELIEF_REQUEST", "relief:${branch?.id}", "Requested edit access for $date.")
                    store.notify("Relief request sent", "${user.name} asked for edit access at ${branch?.name} for $date.", branch?.id, date)
                })
                QuietAction("Simulate branch grant", onClick = {
                    store.reliefEdit.value = true
                    store.audit("Maria Cruz", "RELIEF_GRANT", "relief:${branch?.id}", "Granted ${user.name} edit access for $date.")
                    store.notify("Relief request approved", "${branch?.name} granted ${user.name} edit access for $date.", branch?.id, date)
                })
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!isHome && store.reliefEdit.value) {
            NoteCard("Edit access granted for $date. Compensation is paid from this branch's drawer.")
            Spacer(Modifier.height(12.dp))
        }
        for (item in store.relief) {
            val itemBranch = store.branches.firstOrNull { it.id == item.branchId }?.name ?: item.branchId
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.person, fontWeight = FontWeight.Medium)
                    Text(
                        "${item.kind.label} · $itemBranch · ${item.dayDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(item.state, when {
                    item.state.contains("Edit") || item.state.contains("Accepted") -> ChipTone.GREEN
                    item.state.contains("Pending") || item.state.contains("sent") -> ChipTone.AMBER
                    item.state.contains("Declined") || item.state.contains("Denied") -> ChipTone.RED
                    else -> ChipTone.GREY
                })
                if (item.kind == FakeReliefKind.INVITE && item.state == "Invite sent") {
                    Spacer(Modifier.width(8.dp))
                    QuietAction("Accept", onClick = {
                        val index = store.relief.indexOf(item)
                        store.relief[index] = item.copy(state = "Accepted — grant written")
                        store.audit(user.name, "RELIEF_ACCEPT", "relief:${item.id}", "Accepted invite for ${item.dayDate}.")
                    })
                    QuietAction("Decline", onClick = {
                        val index = store.relief.indexOf(item)
                        store.relief[index] = item.copy(state = "Declined")
                        store.audit(user.name, "RELIEF_DECLINE", "relief:${item.id}", "Declined invite for ${item.dayDate}.")
                    })
                }
                if (item.kind == FakeReliefKind.REQUEST && item.state == "Pending branch grant" && item.person == user.name) {
                    Spacer(Modifier.width(8.dp))
                    QuietAction("Withdraw", onClick = {
                        val index = store.relief.indexOf(item)
                        store.relief[index] = item.copy(state = "Withdrawn")
                        store.audit(user.name, "RELIEF_WITHDRAW", "relief:${item.id}", "Withdrew own request.")
                    })
                }
            }
        }
    }
}

// ---------- Sessions ----------

@Composable
private fun SessionsScreen(store: ClinicalLightStore, user: FakeUser) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val branch = store.currentBranch()
    val date = store.homeDayFilter.value
    val daySessions = store.sessions.filter { it.branchId == branch?.id && it.dayDate == date }
    val visible = if (filter == null) daySessions else daySessions.filter { it.status.name == filter }

    Row(verticalAlignment = Alignment.Top) {
        Text("Sessions", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        PrimaryAction("New session", onClick = { showCreate = true })
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "${branch?.name} · $date — one visit, one client, one branch.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill("All", filter == null, onClick = { filter = null })
        for (status in FakeSessionStatus.entries) {
            FilterPill(status.label, filter == status.name, onClick = { filter = status.name })
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(ClinicalGap), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (visible.isEmpty()) {
                SectionCard(title = "Nothing here", subtitle = "No sessions match this filter for the selected day.") {}
            }
            for (session in visible) {
                val selected = session.id == selectedId
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(ClinicalRadius))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(ClinicalRadius))
                        .clickable { selectedId = session.id }
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${session.time} · ${session.clientName}", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${session.id} · ${session.type} · ${peso(session.price)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (session.walkIn) {
                            StatusChip("Walk-in", ChipTone.BLUE)
                            Spacer(Modifier.width(8.dp))
                        }
                        StatusChip(
                            if (session.voided) "Voided" else session.status.label,
                            sessionTone(session.status, session.voided),
                        )
                    }
                }
            }
        }
        val selected = store.sessions.firstOrNull { it.id == selectedId }
        Box(Modifier.weight(1f)) {
            if (selected == null) {
                NoteCard("Select a session to see details, change status, or void with a reason.")
            } else {
                SessionDetail(store, user, selected)
            }
        }
    }
    if (showCreate) {
        CreateSessionDialog(store, user, onClose = { showCreate = false })
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SessionDetail(store: ClinicalLightStore, user: FakeUser, session: FakeSession) {
    var showVoid by rememberSaveable { mutableStateOf(false) }
    var voidReason by rememberSaveable(session.id) { mutableStateOf("") }
    SectionCard(title = "${session.id} · ${session.clientName}", subtitle = "${session.type} · ${session.practitioners}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(session.status.label, sessionTone(session.status, session.voided))
            Spacer(Modifier.width(8.dp))
            if (session.voided) StatusChip("Voided", ChipTone.GREY)
            if (session.walkIn) {
                Spacer(Modifier.width(8.dp))
                StatusChip("Walk-in", ChipTone.BLUE)
            }
        }
        Spacer(Modifier.height(12.dp))
        KeyValue("Time", session.time)
        Spacer(Modifier.height(6.dp))
        KeyValue("Final price", peso(session.price))
        Spacer(Modifier.height(6.dp))
        KeyValue("Branch day", session.dayDate)
        if (session.voided) {
            Spacer(Modifier.height(12.dp))
            NoteCard("Voided — excluded from financial totals but kept on record. Reason: ${session.voidReason ?: "—"}")
        }
        if (session.walkIn) {
            Spacer(Modifier.height(12.dp))
            NoteCard("Walk-in rule: walk-in sessions cannot be marked No-show or Cancelled.")
        }
        Spacer(Modifier.height(16.dp))
        if (session.status == FakeSessionStatus.PENDING && !session.voided) {
            Text("Move to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryAction("Complete", onClick = {
                    updateSession(store, user, session) { it.copy(status = FakeSessionStatus.COMPLETED) }
                    clearClientPending(store, session)
                })
                OutlinedButton(
                    onClick = {
                        updateSession(store, user, session) { it.copy(status = FakeSessionStatus.NO_SHOW) }
                        clearClientPending(store, session)
                    },
                    enabled = !session.walkIn,
                ) { Text("No-show") }
                OutlinedButton(
                    onClick = {
                        updateSession(store, user, session) { it.copy(status = FakeSessionStatus.CANCELLED) }
                        clearClientPending(store, session)
                    },
                    enabled = !session.walkIn,
                ) { Text("Cancel") }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.voided) {
                OutlinedButton(onClick = {
                    updateSession(store, user, session) { it.copy(voided = false, voidReason = null) }
                    store.audit(user.name, "UNVOID", "sessions:${session.id}", "Unvoided — restored to financial totals.")
                }) { Text("Unvoid") }
            } else {
                OutlinedButton(onClick = { showVoid = true }) { Text("Void with reason") }
            }
        }
    }
    if (showVoid) {
        AlertDialog(
            onDismissRequest = { showVoid = false },
            title = { Text("Void ${session.id}") },
            text = {
                Column {
                    Text("Voided sessions leave financial totals but stay visible. A reason is required.")
                    Spacer(Modifier.height(12.dp))
                    TextField(value = voidReason, onValueChange = { voidReason = it }, label = { Text("Reason") })
                }
            },
            confirmButton = {
                PrimaryAction("Void session", enabled = voidReason.isNotBlank(), onClick = {
                    updateSession(store, user, session) { it.copy(voided = true, voidReason = voidReason.trim()) }
                    store.audit(user.name, "VOID", "sessions:${session.id}", "Voided: ${voidReason.trim()}")
                    showVoid = false
                })
            },
            dismissButton = { QuietAction("Keep session", onClick = { showVoid = false }) },
        )
    }
}

private fun updateSession(store: ClinicalLightStore, user: FakeUser, session: FakeSession, change: (FakeSession) -> FakeSession) {
    val index = store.sessions.indexOfFirst { it.id == session.id }
    if (index < 0) return
    val before = store.sessions[index]
    val after = change(before)
    store.sessions[index] = after
    if (before.status != after.status) {
        store.audit(user.name, "STATUS", "sessions:${session.id}", "${before.status} -> ${after.status}.")
    }
}

private fun clearClientPending(store: ClinicalLightStore, session: FakeSession) {
    val index = store.clients.indexOfFirst { it.id == session.clientId && it.pendingSessionId == session.id }
    if (index >= 0) {
        store.clients[index] = store.clients[index].copy(pendingSessionId = null)
    }
}

@Composable
private fun CreateSessionDialog(store: ClinicalLightStore, user: FakeUser, onClose: () -> Unit) {
    var clientName by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("15:00") }
    var type by rememberSaveable { mutableStateOf("Follow-up") }
    var price by rememberSaveable { mutableStateOf("1200") }
    var walkIn by rememberSaveable { mutableStateOf(false) }
    val branch = store.currentBranch()
    val date = store.homeDayFilter.value
    val blockedClient = store.clients.firstOrNull {
        it.name.equals(clientName.trim(), ignoreCase = true) && it.pendingSessionId != null
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("New session") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${branch?.name} · $date — type is auto-assigned from client history in production; pick freely here.")
                Spacer(Modifier.height(12.dp))
                TextField(value = clientName, onValueChange = { clientName = it }, label = { Text("Client name") })
                Spacer(Modifier.height(8.dp))
                TextField(value = time, onValueChange = { time = it }, label = { Text("Time") })
                Spacer(Modifier.height(8.dp))
                TextField(value = type, onValueChange = { type = it }, label = { Text("Session type") })
                Spacer(Modifier.height(8.dp))
                TextField(value = price, onValueChange = { price = it }, label = { Text("Final price") })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { walkIn = !walkIn }) {
                    Checkbox(checked = walkIn, onCheckedChange = { walkIn = it })
                    Text("Walk-in (no No-show / Cancelled)")
                }
                if (blockedClient != null) {
                    Spacer(Modifier.height(8.dp))
                    NoteCard("At most one Pending session per client: ${blockedClient.name} already holds ${blockedClient.pendingSessionId}.")
                }
            }
        },
        confirmButton = {
            PrimaryAction("Create pending", enabled = clientName.isNotBlank() && blockedClient == null, onClick = {
                val id = "S-${110 + store.sessions.size}"
                val priceValue = price.toIntOrNull() ?: 0
                val existing = store.clients.firstOrNull { it.name.equals(clientName.trim(), ignoreCase = true) }
                val clientId = existing?.id ?: "c-new-${store.clients.size}"
                if (existing == null) {
                    store.clients.add(FakeClient(clientId, clientName.trim(), 35, "F", "09xx-xxx-xxxx", pendingSessionId = id))
                } else {
                    val index = store.clients.indexOf(existing)
                    store.clients[index] = existing.copy(pendingSessionId = id)
                }
                store.sessions.add(
                    FakeSession(
                        id = id, time = time.trim(), clientId = clientId, clientName = clientName.trim(),
                        branchId = branch?.id ?: "", dayDate = date, type = type.trim(),
                        status = FakeSessionStatus.PENDING, price = priceValue,
                        practitioners = user.name, walkIn = walkIn,
                    ),
                )
                store.audit(user.name, "INSERT", "sessions:$id", "Created pending session for $clientName.")
                store.notify("Session created", "$id for $clientName at ${branch?.name}.", branch?.id, date)
                onClose()
            })
        },
        dismissButton = { QuietAction("Discard", onClick = onClose) },
    )
}

// ---------- Clients ----------

@Composable
private fun ClientsScreen(store: ClinicalLightStore, user: FakeUser) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    Text("Clients", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        "Global records shared across all branches — not per-branch lists.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    TextField(value = query, onValueChange = { query = it }, label = { Text("Search clients") }, modifier = Modifier.width(360.dp))
    Spacer(Modifier.height(16.dp))
    NoteCard("Rule: a client holds at most one Pending session at a time. Anonymized records keep gender and age for reporting; PII is nullified.")
    Spacer(Modifier.height(16.dp))
    val visible = store.clients.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    Row(horizontalArrangement = Arrangement.spacedBy(ClinicalGap)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (client in visible) {
                val selected = client.id == selectedId
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(ClinicalRadius))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(ClinicalRadius))
                        .clickable { selectedId = client.id }.padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(client.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${client.gender} · age ${client.age}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (client.anonymized) StatusChip("Anonymized", ChipTone.GREY)
                        else if (client.pendingSessionId != null) StatusChip("1 pending", ChipTone.AMBER)
                        else StatusChip("Clear", ChipTone.GREEN)
                    }
                }
            }
        }
        val selected = store.clients.firstOrNull { it.id == selectedId }
        Box(Modifier.weight(1f)) {
            if (selected == null) {
                NoteCard("Select a client to see the full record.")
            } else {
                SectionCard(title = selected.name, subtitle = "Global client record ${selected.id}") {
                    if (selected.anonymized) {
                        NoteCard("Anonymized view: name and contact are withheld. Gender and age stay for reporting.")
                        Spacer(Modifier.height(12.dp))
                        KeyValue("Gender", selected.gender)
                        Spacer(Modifier.height(6.dp))
                        KeyValue("Age", selected.age.toString())
                        Spacer(Modifier.height(6.dp))
                        KeyValue("Phone", "withheld")
                    } else {
                        KeyValue("Age", selected.age.toString())
                        Spacer(Modifier.height(6.dp))
                        KeyValue("Gender", selected.gender)
                        Spacer(Modifier.height(6.dp))
                        KeyValue("Phone", selected.phone)
                        Spacer(Modifier.height(6.dp))
                        KeyValue("Pending session", selected.pendingSessionId ?: "none")
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = {
                            val index = store.clients.indexOf(selected)
                            store.clients[index] = selected.copy(name = "Anonymized record", phone = "withheld", anonymized = true)
                            store.audit(user.name, "ANONYMIZE", "clients:${selected.id}", "Soft-deleted PII; kept gender and age.")
                        }) { Text("Anonymize record") }
                    }
                }
            }
        }
    }
}

// ---------- Finance ----------

@Composable
private fun FinanceScreen(store: ClinicalLightStore, user: FakeUser) {
    var kind by rememberSaveable { mutableStateOf(FakeRemitKind.SESSION) }
    var undoTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var undoReason by rememberSaveable { mutableStateOf("") }
    val branch = store.currentBranch()
    val branchRemits = store.remittances.filter { it.branchId == branch?.id && it.kind == kind }
    val productTotal = store.productLines.sumOf { it.total }
    val included = store.commissionIncluded
    val split = if (included.isEmpty()) 0 else productTotal / included.size

    Text("Finance", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        "Two independent flows: Session income and Product sales. Submission freezes an immutable snapshot.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill("Session flow", kind == FakeRemitKind.SESSION, onClick = { kind = FakeRemitKind.SESSION })
        FilterPill("Product flow", kind == FakeRemitKind.PRODUCT, onClick = { kind = FakeRemitKind.PRODUCT })
    }
    Spacer(Modifier.height(16.dp))

    if (kind == FakeRemitKind.PRODUCT) {
        SectionCard(title = "Product lines", subtitle = "Unit price × quantity · pooled per branch day") {
            for (line in store.productLines) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(line.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${line.qty} × ${peso(line.unitPrice)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = {
                        val index = store.productLines.indexOf(line)
                        if (line.qty > 1) store.productLines[index] = line.copy(qty = line.qty - 1)
                    }) { Text("−") }
                    Spacer(Modifier.width(8.dp))
                    Text(peso(line.total), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        val index = store.productLines.indexOf(line)
                        store.productLines[index] = line.copy(qty = line.qty + 1)
                    }) { Text("+") }
                }
            }
            Spacer(Modifier.height(8.dp))
            KeyValue("Product pool total", peso(productTotal))
        }
        Spacer(Modifier.height(ClinicalGap))
        SectionCard(
            title = "Commission split",
            subtitle = "Pooled per branch day · split equally among clocked-in practitioners and coordinators at sold_at",
        ) {
            for (name in listOf("Dra. Amara Santos", "Maria Cruz", "Liam Tan")) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = name in included,
                        onCheckedChange = { on ->
                            if (on) included.add(name) else included.remove(name)
                            store.audit(user.name, "COMMISSION_OVERRIDE", "finance:commission", "$name ${if (on) "included" else "excluded"}.")
                        },
                    )
                    Text(name, modifier = Modifier.weight(1f))
                    Text(if (name in included) peso(split) else "excluded")
                }
            }
            Spacer(Modifier.height(8.dp))
            NoteCard("Manual inclusions and exclusions override the pool. Commissions are separate from compensation and never remitted.")
        }
        Spacer(Modifier.height(ClinicalGap))
    }

    for (remit in branchRemits) {
        SectionCard(
            title = "${remit.id} · ${remit.dayDate}",
            subtitle = remit.note,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(remit.state.label, if (remit.state == FakeSubmission.DRAFT) ChipTone.AMBER else ChipTone.GREEN)
                if (remit.snapshotId != null) {
                    Spacer(Modifier.width(8.dp))
                    StatusChip("Snapshot ${remit.snapshotId}", ChipTone.BLUE)
                }
            }
            Spacer(Modifier.height(12.dp))
            KeyValue("Gross", peso(if (remit.kind == FakeRemitKind.PRODUCT && remit.state == FakeSubmission.DRAFT) productTotal else remit.gross))
            Spacer(Modifier.height(6.dp))
            KeyValue("Compensation + expenses", peso(remit.deductions))
            Spacer(Modifier.height(6.dp))
            KeyValue("Net", peso((if (remit.kind == FakeRemitKind.PRODUCT && remit.state == FakeSubmission.DRAFT) productTotal else remit.gross) - remit.deductions))
            if (remit.submittedAt != null) {
                Spacer(Modifier.height(6.dp))
                KeyValue("Submitted", remit.submittedAt)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (remit.state == FakeSubmission.DRAFT) {
                    PrimaryAction("Submit remittance", onClick = {
                        val index = store.remittances.indexOf(remit)
                        val gross = if (remit.kind == FakeRemitKind.PRODUCT) productTotal else remit.gross
                        val snap = "SNAP-${8800 + store.remittances.size * 7}"
                        store.remittances[index] = remit.copy(
                            state = FakeSubmission.SUBMITTED, gross = gross,
                            snapshotId = snap, submittedAt = "2026-09-09 09:41 Asia/Manila",
                        )
                        store.audit(user.name, "REMIT_SUBMIT", "remittances:${remit.id}", "Submitted. Snapshot $snap frozen.")
                        store.notify("Remittance submitted", "${remit.id} submitted. Snapshot $snap frozen.", remit.branchId, remit.dayDate)
                    })
                    OutlinedButton(onClick = {
                        val index = store.remittances.indexOf(remit)
                        store.remittances[index] = remit.copy(deductions = remit.deductions + 100)
                    }) { Text("Add ₱100 expense") }
                } else {
                    OutlinedButton(onClick = {
                        undoTarget = remit.id
                        undoReason = ""
                    }) { Text("Undo within 48h") }
                }
            }
        }
        Spacer(Modifier.height(ClinicalGap))
    }
    NoteCard("Undo returns a remittance to Draft, unlocks the covered days and deletes the frozen snapshot. It needs a reason and only works inside 48 hours of submission — afterwards the snapshot is permanent.")
    if (undoTarget != null) {
        val target = store.remittances.firstOrNull { it.id == undoTarget }
        AlertDialog(
            onDismissRequest = { undoTarget = null },
            title = { Text("Undo ${target?.id}") },
            text = {
                Column {
                    Text("Snapshot ${target?.snapshotId} will be deleted and the day reopens. A reason is recorded in the audit trail.")
                    Spacer(Modifier.height(12.dp))
                    TextField(value = undoReason, onValueChange = { undoReason = it }, label = { Text("Reason") })
                }
            },
            confirmButton = {
                PrimaryAction("Undo submission", enabled = undoReason.isNotBlank(), onClick = {
                    val index = store.remittances.indexOfFirst { it.id == undoTarget }
                    if (index >= 0 && target != null) {
                        store.remittances[index] = target.copy(
                            state = FakeSubmission.DRAFT, snapshotId = null,
                            submittedAt = null, undoReason = undoReason.trim(),
                        )
                        store.audit(user.name, "REMIT_UNDO", "remittances:${target.id}", "Undone: ${undoReason.trim()}")
                        store.notify("Remittance undone", "${target.id} returned to Draft.", target.branchId, target.dayDate)
                    }
                    undoTarget = null
                })
            },
            dismissButton = { QuietAction("Keep submitted", onClick = { undoTarget = null }) },
        )
    }
}

// ---------- Team ----------

@Composable
private fun TeamScreen(store: ClinicalLightStore) {
    Text("Team", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        "Users, roles and branch slots. Slot 1 reads first in reports; relief sorts after home slots.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    SectionCard(title = "People", subtitle = "${store.users.size} users across ${store.branches.size} branches") {
        for (person in store.users.sortedBy { it.slot }) {
            val home = store.branches.firstOrNull { it.id == person.homeBranchId }?.name ?: "—"
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(person.name, fontWeight = FontWeight.Medium)
                    Text(
                        "Home: $home · Slot ${person.slot}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(person.role.label, if (person.role == FakeRole.ONBOARDING) ChipTone.GREY else ChipTone.BLUE)
            }
        }
    }
    Spacer(Modifier.height(ClinicalGap))
    Text("Role bundles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (role in FakeRole.entries) {
            SectionCard(title = role.label) {
                Text(role.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- Mailbox ----------

@Composable
private fun MailboxScreen(store: ClinicalLightStore, onOpenDay: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Mailbox", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        QuietAction("Mark all read", onClick = {
            for (i in store.notifications.indices) {
                store.notifications[i] = store.notifications[i].copy(read = true)
            }
        })
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "Every message is stored per recipient. Read rows are kept forever as history.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(560.dp)) {
        items(store.notifications, key = { it.id }) { note ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(ClinicalRadius))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(ClinicalRadius))
                    .clickable {
                        val index = store.notifications.indexOf(note)
                        store.notifications[index] = note.copy(read = true)
                    }.padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(999.dp))
                            .background(if (note.read) Color.Transparent else MaterialTheme.colorScheme.primary)
                            .padding(5.dp),
                    ) {}
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(note.title, fontWeight = if (note.read) FontWeight.Normal else FontWeight.SemiBold)
                        Text(note.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (note.branchId != null && note.dayDate != null) {
                        Spacer(Modifier.width(8.dp))
                        QuietAction("Open day", onClick = {
                            store.currentBranchId.value = note.branchId
                            store.homeDayFilter.value = note.dayDate
                            val index = store.notifications.indexOf(note)
                            store.notifications[index] = note.copy(read = true)
                            onOpenDay()
                        })
                    }
                }
            }
        }
    }
}

// ---------- Audit ----------

@Composable
private fun AuditScreen(store: ClinicalLightStore) {
    Text("Audit log", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        "Immutable record of every mutation — who changed what, when, and why. Newest first.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(600.dp)) {
        items(store.audit, key = { it.id }) { entry ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(ClinicalRadius))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(ClinicalRadius))
                    .padding(16.dp),
            ) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("${entry.action} · ${entry.target}", fontWeight = FontWeight.SemiBold)
                        Text(entry.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(entry.actor, style = MaterialTheme.typography.labelLarge)
                        Text(entry.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------- Profile ----------

@Composable
private fun ProfileScreen(store: ClinicalLightStore, user: FakeUser) {
    Text("Profile", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
    SectionCard(title = user.name, subtitle = "${user.role.label} · Slot ${user.slot}") {
        Text(user.role.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        val home = store.branches.firstOrNull { it.id == user.homeBranchId }?.name ?: "—"
        KeyValue("Home branch", home)
        Spacer(Modifier.height(6.dp))
        KeyValue("Status", if (store.clockedIn.value) "Clocked in" else "Clocked out")
    }
    Spacer(Modifier.height(ClinicalGap))
    SectionCard(title = "End the day") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (store.clockedIn.value) {
                OutlinedButton(onClick = {
                    store.clockedIn.value = false
                    store.audit(user.name, "CLOCK_OUT", "attendance:${store.currentBranchId.value}", "Clocked out from profile.")
                }) { Text("Clock out") }
            }
            PrimaryAction("Sign out", onClick = {
                store.audit(user.name, "LOGOUT", "auth:${user.id}", "Signed out from profile.")
                store.currentUserId.value = null
                store.currentBranchId.value = null
                store.clockedIn.value = false
            })
        }
    }
}
