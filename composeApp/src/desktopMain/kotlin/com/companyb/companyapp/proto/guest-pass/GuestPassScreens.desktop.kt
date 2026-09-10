package com.companyb.companyapp.proto.guestpass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
fun ScreenScroll(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
fun LoginScreen(repo: GuestPassRepo) {
    ScreenScroll {
        SectionTitle("Visitor desk", "Guest-pass prototype · fake data only · no network calls")
        PassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StampChip("guest pass", StampKind.PLUM)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Pick a badge to enter. Outsiders start view-only; grants are celebrated.",
                    fontSize = 13.sp,
                    color = GuestPassColors.Muted,
                )
            }
        }
        repo.users.forEach { user ->
            PassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(user.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GuestPassColors.Ink)
                        MonoId("badge ${user.id} · home ${repo.branchName(user.homeBranchId)}")
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StampChip(
                            user.role.name,
                            if (user.role == FakeRole.ONBOARDING) StampKind.RED else StampKind.SLATE,
                        )
                        PassButton("Enter", onClick = { repo.login(user) }, primary = true)
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingLockedScreen(repo: GuestPassRepo) {
    ScreenScroll {
        SectionTitle("Locked badge", "ONBOARDING holds an empty capability bundle")
        PassCard {
            StampChip("locked", StampKind.RED)
            Spacer(Modifier.height(4.dp))
            Text(
                "This badge carries zero capabilities — locked out even with a branch assignment " +
                    "until MANAGE_USERS grants a real role.",
                fontSize = 14.sp,
                color = GuestPassColors.Ink,
            )
            Perforation()
            PassButton("Sign out", onClick = { repo.logout() })
        }
    }
}

@Composable
fun BranchSelectScreen(repo: GuestPassRepo) {
    ScreenScroll {
        SectionTitle("Choose your counter", "Home branch opens fully · relief branches open view-only")
        repo.branches.forEach { branch ->
            val isHome = branch.id == repo.currentUser?.homeBranchId
            val selected = branch.id == repo.selectedBranchId
            PassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(branch.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GuestPassColors.Ink)
                        MonoId("${branch.kind} · day ${repo.dayStates[branch.id]}")
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isHome) StampChip("home", StampKind.GREEN) else StampChip("relief", StampKind.AMBER)
                        if (selected) StampChip("at counter", StampKind.PLUM)
                        PassButton("Open", onClick = { repo.selectedBranchId = branch.id })
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(repo: GuestPassRepo) {
    val user = repo.currentUser ?: return
    val branchId = repo.selectedBranchId
    ScreenScroll {
        SectionTitle("Duty pass · ${repo.branchName(branchId)}", "Clock in to activate this pass")
        if (!repo.canEdit(branchId)) ViewOnlyRibbon(repo.branchName(branchId))
        repo.celebration?.let { GrantCelebration(it) }
        PassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        if (repo.clockedIn) "Clocked in · ${repo.branchName(repo.clockBranchId ?: "")}" else "Off duty",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GuestPassColors.Ink,
                    )
                    MonoId("badge ${user.id} · ${user.role}")
                }
                StampChip(
                    if (repo.clockedIn) "on duty" else "off duty",
                    if (repo.clockedIn) StampKind.GREEN else StampKind.SLATE,
                )
            }
            Perforation()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PassButton(
                    "Clock in home",
                    onClick = { repo.clockIn(user.homeBranchId, false) },
                    enabled = !repo.clockedIn,
                    primary = true,
                )
                PassButton(
                    "Clock in relief here",
                    onClick = { repo.clockIn(branchId, branchId != user.homeBranchId) },
                    enabled = !repo.clockedIn,
                )
                PassButton("Clock out", onClick = { repo.clockOut() }, enabled = repo.clockedIn)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fake clock:", fontSize = 13.sp, color = GuestPassColors.Muted)
                PassButton("-", onClick = { repo.fakeHour = (repo.fakeHour + 23) % 24 })
                Text(
                    "${repo.fakeHour}:00 Manila",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GuestPassColors.Ink,
                )
                PassButton("+", onClick = { repo.fakeHour = (repo.fakeHour + 1) % 24 })
                MonoId("grants die 04:00 · ${repo.hoursToExpiry()}h left")
            }
        }
        SectionTitle("Relief counters", "View-only until a grant lands — grants are celebrated above")
        repo.branches.filter { it.id != user.homeBranchId }.forEach { branch ->
            PassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(branch.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GuestPassColors.Ink)
                        MonoId(
                            if (branch.id in repo.grants) "edit grant · expires 04:00" else "view only · no grant",
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PassButton(
                            "Request",
                            onClick = { repo.audit("Relief request broadcast at ${branch.name}") },
                        )
                        PassButton("Simulate grant", onClick = { repo.simulateGrant(branch.id) })
                        PassButton("Withdraw", onClick = { repo.withdrawRequest(branch.id) })
                    }
                }
            }
        }
        SectionTitle("Invites", "Branch-initiated offers — accepting writes the day grant")
        repo.notices.filter { it.branchId != null }.forEach { notice ->
            PassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(notice.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GuestPassColors.Ink)
                        Text(notice.body, fontSize = 13.sp, color = GuestPassColors.Muted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PassButton("Accept", onClick = { repo.acceptInvite(notice.id) }, primary = true)
                        PassButton(
                            "Decline",
                            onClick = {
                                repo.markRead(notice.id)
                                repo.audit("Declined invite: ${notice.title}")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionsScreen(repo: GuestPassRepo) {
    val branchId = repo.selectedBranchId
    var filter by remember { mutableStateOf<SessionStatus?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val editable = repo.canEdit(branchId)
    ScreenScroll {
        SectionTitle("Sessions · ${repo.branchName(branchId)}", "PENDING → COMPLETED / NO_SHOW / CANCELLED")
        if (!editable) ViewOnlyRibbon(repo.branchName(branchId))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PassButton("All", onClick = { filter = null }, primary = filter == null)
            SessionStatus.entries.forEach { st ->
                PassButton(st.name, onClick = { filter = st }, primary = filter == st)
            }
            Spacer(Modifier.width(8.dp))
            PassButton("+ New session", onClick = { showNew = true }, enabled = editable, primary = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val stubs = repo.sessions
                    .filter { it.branchId == branchId && (filter == null || it.status == filter) }
                stubs.forEach { s ->
                    PassCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable { selectedId = s.id }.padding(2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    s.clientName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GuestPassColors.Ink,
                                )
                                StampChip(
                                    if (s.voided) "voided" else s.status.name,
                                    if (s.voided) StampKind.SLATE else when (s.status) {
                                        SessionStatus.PENDING -> StampKind.AMBER
                                        SessionStatus.COMPLETED -> StampKind.GREEN
                                        SessionStatus.NO_SHOW -> StampKind.RED
                                        SessionStatus.CANCELLED -> StampKind.RED
                                    },
                                )
                            }
                            MonoId("${s.code} · ${s.practitioner} · ₱${s.price}${if (s.walkIn) " · walk-in" else ""}")
                        }
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val detail = repo.sessions.firstOrNull { it.id == selectedId && it.branchId == branchId }
                if (detail == null) {
                    PassCard {
                        Text(
                            "Tap a session stub to inspect it.",
                            fontSize = 13.sp,
                            color = GuestPassColors.Muted,
                        )
                    }
                } else {
                    SessionDetail(repo, detail, editable)
                }
            }
        }
    }
    if (showNew) NewSessionDialog(repo, onClose = { showNew = false })
}

@Composable
fun SessionDetail(
    repo: GuestPassRepo,
    s: PassSession,
    editable: Boolean,
) {
    var showVoid by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    PassCard {
        SectionTitle(s.code, "${s.clientName} · ${s.practitioner}")
        if (s.walkIn) {
            Text(
                "Walk-in rule: NO_SHOW and CANCELLED are disabled for walk-ins.",
                fontSize = 13.sp,
                color = GuestPassColors.Amber,
            )
        }
        if (s.voided) {
            StampChip("voided · excluded from totals", StampKind.SLATE)
            Text("Reason: ${s.voidReason}", fontSize = 13.sp, color = GuestPassColors.Muted)
        }
        LabeledValue("Status", s.status.name)
        LabeledValue("Price", "₱${s.price}")
        LabeledValue("Walk-in", if (s.walkIn) "yes" else "no")
        Perforation()
        Text("Move status:", fontSize = 13.sp, color = GuestPassColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PassButton(
                "Completed",
                onClick = { repo.setStatus(s.id, SessionStatus.COMPLETED) },
                enabled = editable && !s.voided,
            )
            PassButton(
                "No-show",
                onClick = { repo.setStatus(s.id, SessionStatus.NO_SHOW) },
                enabled = editable && !s.voided && !s.walkIn,
            )
            PassButton(
                "Cancelled",
                onClick = { repo.setStatus(s.id, SessionStatus.CANCELLED) },
                enabled = editable && !s.voided && !s.walkIn,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.voided) {
                PassButton("Unvoid (restore)", onClick = { repo.unvoidSession(s.id) }, enabled = editable)
            } else {
                PassButton("Void with reason", onClick = { showVoid = true }, enabled = editable)
            }
        }
    }
    if (showVoid) {
        AlertDialog(
            onDismissRequest = { showVoid = false },
            title = { Text("Void ${s.code}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A reason is required — it stays on the audit trail.", fontSize = 13.sp)
                    TextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (reason.isNotBlank()) {
                            repo.voidSession(s.id, reason.trim())
                            showVoid = false
                        }
                    },
                ) { Text("Void") }
            },
            dismissButton = { TextButton(onClick = { showVoid = false }) { Text("Keep") } },
        )
    }
}

@Composable
fun NewSessionDialog(
    repo: GuestPassRepo,
    onClose: () -> Unit,
) {
    var pickedId by remember { mutableStateOf<String?>(null) }
    var walkIn by remember { mutableStateOf(false) }
    var priceText by remember { mutableStateOf("1200") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("New session · ${repo.branchName(repo.selectedBranchId)}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("One PENDING session per client — blocked clients are stamped.", fontSize = 13.sp)
                repo.clients.forEach { client ->
                    val blocked = repo.clientHasPending(client.id)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { pickedId = client.id }.padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (client.anonymized) "Anonymized · ${client.gender} · ${client.age}" else client.name,
                            fontSize = 13.sp,
                            fontWeight = if (pickedId == client.id) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (blocked) {
                            StampChip("has pending", StampKind.AMBER)
                        } else if (pickedId == client.id) {
                            StampChip("picked", StampKind.GREEN)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = walkIn, onCheckedChange = { walkIn = it })
                    Text("Walk-in (no NO_SHOW / CANCELLED later)", fontSize = 13.sp)
                }
                TextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                    label = { Text("Price ₱") },
                    singleLine = true,
                )
                error?.let { Text(it, fontSize = 13.sp, color = GuestPassColors.Oxblood) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = pickedId ?: run {
                        error = "pick a client first"
                        return@TextButton
                    }
                    val err = repo.createSession(id, walkIn, priceText.toIntOrNull() ?: 0)
                    if (err == null) onClose() else error = err
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
fun ClientsScreen(repo: GuestPassRepo) {
    var query by remember { mutableStateOf("") }
    ScreenScroll {
        SectionTitle("Client registry", "Global across branches · at most one PENDING session per client")
        PassCard {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search clients") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        repo.clients.filter { it.name.contains(query, ignoreCase = true) }.forEach { client ->
            val pending = repo.sessions.any {
                it.clientId == client.id && it.status == SessionStatus.PENDING && !it.voided
            }
            PassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        if (client.anonymized) {
                            Text(
                                "Anonymized record",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = GuestPassColors.Ink,
                            )
                            MonoId("PII removed · retains ${client.gender} · age ${client.age}")
                        } else {
                            Text(
                                client.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = GuestPassColors.Ink,
                            )
                            MonoId("${client.gender} · age ${client.age} · ${client.id}")
                        }
                    }
                    if (client.anonymized) {
                        StampChip("anonymized", StampKind.SLATE)
                    } else if (pending) {
                        StampChip("has pending", StampKind.AMBER)
                    } else {
                        StampChip("clear", StampKind.GREEN)
                    }
                }
            }
        }
    }
}

@Composable
fun FinanceScreen(repo: GuestPassRepo) {
    val branchId = repo.selectedBranchId
    val editable = repo.canEdit(branchId)
    var undoTarget by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    ScreenScroll {
        SectionTitle(
            "Remittance counter · ${repo.branchName(branchId)}",
            "SESSION + PRODUCT · snapshot freezes at submit · undo within 48h",
        )
        if (!editable) ViewOnlyRibbon(repo.branchName(branchId))
        val list = repo.remittances.filter { it.branchId == branchId }
        if (list.isEmpty()) PassCard { Text("No remittances at this branch yet.", fontSize = 13.sp) }
        list.forEach { remit ->
            PassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Remittance ${remit.id}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GuestPassColors.Ink,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StampChip(remit.kind, StampKind.PLUM)
                        StampChip(
                            remit.state.name,
                            if (remit.state == RemitState.SUBMITTED) StampKind.GREEN else StampKind.AMBER,
                        )
                    }
                }
                if (remit.kind == "PRODUCT") {
                    repo.productLines.forEach { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${line.name} · ₱${line.price}", fontSize = 13.sp, color = GuestPassColors.Ink)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PassButton(
                                    "-",
                                    onClick = { repo.bumpQty(line.id, -1) },
                                    enabled = editable && remit.state == RemitState.DRAFT,
                                )
                                Text(
                                    "${line.qty}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GuestPassColors.Ink,
                                )
                                PassButton(
                                    "+",
                                    onClick = { repo.bumpQty(line.id, 1) },
                                    enabled = editable && remit.state == RemitState.DRAFT,
                                )
                            }
                        }
                    }
                    LabeledValue("Pool total", "₱${repo.productTotal()}")
                } else {
                    LabeledValue("Net pool", "₱${remit.amount}")
                    val staff = repo.checkedInStaff()
                    val perHead = if (staff.isNotEmpty()) remit.amount / staff.size else 0
                    Text(
                        "Commission split: pool divides across ${staff.size} checked-in staff " +
                            "(₱$perHead each). Overrides are audited.",
                        fontSize = 13.sp,
                        color = GuestPassColors.Muted,
                    )
                    staff.forEach { LabeledValue("· $it", "on split") }
                }
                if (remit.snapshot.isNotEmpty()) MonoId(remit.snapshot)
                Perforation()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PassButton(
                        "Submit (freeze snapshot)",
                        onClick = { repo.submitRemit(remit.id) },
                        enabled = editable && remit.state == RemitState.DRAFT,
                        primary = true,
                    )
                    PassButton(
                        "Undo 48h",
                        onClick = { undoTarget = remit.id },
                        enabled = editable && remit.state == RemitState.SUBMITTED,
                    )
                }
            }
        }
    }
    undoTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { undoTarget = null },
            title = { Text("Undo remittance $id") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Within 48h of submit: returns to draft, unlocks days, deletes the snapshot.",
                        fontSize = 13.sp,
                    )
                    TextField(
                        value = undoReason,
                        onValueChange = { undoReason = it },
                        label = { Text("Reason (required)") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (undoReason.isNotBlank()) {
                            repo.undoRemit(id, undoReason.trim())
                            undoTarget = null
                            undoReason = ""
                        }
                    },
                ) { Text("Undo") }
            },
            dismissButton = { TextButton(onClick = { undoTarget = null }) { Text("Keep") } },
        )
    }
}

@Composable
fun TeamScreen(repo: GuestPassRepo) {
    ScreenScroll {
        SectionTitle("Who is on the floor", "Users, home branches and slots")
        repo.team.forEach { member ->
            PassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(member.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GuestPassColors.Ink)
                        MonoId("${member.homeBranch} · ${member.slots}")
                    }
                    StampChip(member.role, if (member.role.startsWith("ONBOARDING")) StampKind.RED else StampKind.SLATE)
                }
            }
        }
        SectionTitle("Role bundles", "ONBOARDING carries an empty bundle — locked")
        repo.roleBundles.forEach { (role, bundle) ->
            PassCard {
                Text(role, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GuestPassColors.Ink)
                Text(bundle, fontSize = 13.sp, color = GuestPassColors.Muted)
            }
        }
    }
}

@Composable
fun MailboxScreen(
    repo: GuestPassRepo,
    onGoHome: () -> Unit,
) {
    ScreenScroll {
        SectionTitle("Mailbox", "Tap a letter to mark it read · relief letters jump to the branch")
        repo.notices.forEach { notice ->
            PassCard {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { repo.markRead(notice.id) }.padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(notice.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GuestPassColors.Ink)
                        Text(notice.body, fontSize = 13.sp, color = GuestPassColors.Muted)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!notice.read) StampChip("unread", StampKind.RED)
                        if (notice.branchId != null) {
                            PassButton(
                                "Open branch",
                                onClick = {
                                    repo.selectedBranchId = notice.branchId
                                    repo.markRead(notice.id)
                                    onGoHome()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditScreen(repo: GuestPassRepo) {
    ScreenScroll {
        SectionTitle("Audit log", "Every stamp above lands here — newest first")
        if (repo.auditLog.isEmpty()) PassCard { Text("Nothing stamped yet this visit.", fontSize = 13.sp) }
        repo.auditLog.forEach { entry ->
            PassCard {
                Text(entry.text, fontSize = 13.sp, color = GuestPassColors.Ink)
                MonoId("#${entry.id} · ${entry.at}")
            }
        }
    }
}

@Composable
fun ProfileScreen(repo: GuestPassRepo) {
    val user = repo.currentUser ?: return
    ScreenScroll {
        SectionTitle("Badge & lanyard", "Profile, capabilities, sign-out")
        PassCard {
            Text(user.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = GuestPassColors.Ink)
            MonoId("badge ${user.id} · home ${repo.branchName(user.homeBranchId)}")
            Perforation()
            Text("Capabilities:", fontSize = 13.sp, color = GuestPassColors.Muted)
            if (user.capabilities.isEmpty()) {
                StampChip("none — locked", StampKind.RED)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    user.capabilities.forEach { StampChip(it, StampKind.SLATE) }
                }
            }
            Perforation()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PassButton("Clock out", onClick = { repo.clockOut() }, enabled = repo.clockedIn)
                PassButton("Log out", onClick = { repo.logout() }, primary = true)
            }
        }
        OutlinedButton(onClick = { repo.logout() }) { Text("Sign out (secondary)") }
    }
}

@Composable
fun FakeBadgeFooter() {
    Box(
        modifier = Modifier.fillMaxWidth().background(GuestPassColors.Ink).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "✂ guest-pass prototype · fake data only · no network calls",
            fontSize = 12.sp,
            color = Color.White,
        )
    }
}
