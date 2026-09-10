package com.companyb.companyapp.proto.solarizedcalm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp

@Composable
fun CalmLogin(
    onEnter: (String) -> Unit,
    onPreviewOnboarding: () -> Unit,
) {
    var address by remember { mutableStateOf("demo@solmar.ph") }
    CalmPage(
        title = "Solarized calm",
        subtitle = "A low-contrast ledger for long shifts. Precision palette, quiet type, nothing shouting.",
    ) {
        CalmSheet {
            CalmMono("base3 ·fdf6e3 — easy on the eyes since morning")
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Work email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            CalmPrimary(label = "Enter calmly", onClick = { onEnter(address.trim()) })
            CalmGhost(label = "Preview the ONBOARDING welcome", onClick = onPreviewOnboarding)
        }
        CalmQuiet("Any email works — this is fake data only, no network calls. ONBOARDING accounts land on a locked mat.")
    }
}

@Composable
fun CalmOnboardingLocked(onBack: () -> Unit) {
    CalmPage(
        title = "ONBOARDING · locked",
        subtitle = "Welcome. Your Capability bundle is empty, so the floor stays veiled until a Coordinator unlocks you.",
        onBack = onBack,
    ) {
        CalmSheet {
            CalmRow(label = "role", value = "ONBOARDING")
            CalmRow(label = "capabilities", value = "0 granted")
            CalmRule()
            CalmBody("You can read this welcome and nothing else. Ask your Coordinator to grant branch, session, and remittance capabilities.")
            CalmChip(text = "locked · 0 capabilities", tone = SolYellow)
        }
    }
}

@Composable
fun CalmBranchSelect(
    repo: CalmFakeRepo,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    CalmPage(
        title = "Choose a Branch",
        subtitle = "Clients are global across Branches. The Branch Day banner follows your pick.",
        onBack = onBack,
    ) {
        repo.branches.forEach { branch ->
            val selected = branch.id == repo.branchId
            val tone =
                when (branch.day) {
                    CalmDay.OPEN -> SolGreen
                    CalmDay.PAST -> SolYellow
                    CalmDay.REMITTED -> SolViolet
                }
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(
                            if (selected) SolBase2 else SolBase2.copy(alpha = 0.45f),
                            RoundedCornerShape(10.dp),
                        )
                        .clickable {
                            repo.branchId = branch.id
                            repo.dayOverride = null
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = branch.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SolBase02)
                        CalmChip(text = branch.day.name, tone = tone)
                    }
                    Text(text = branch.place, fontSize = 13.sp, color = SolBase00)
                    if (selected) {
                        Text(text = "✓ selected", fontSize = 12.sp, fontFamily = SolMono, color = SolBlue)
                    }
                }
            }
        }
        CalmPrimary(label = "Open ${repo.currentBranch().name}", onClick = onPick)
    }
}

@Composable
fun CalmHome(repo: CalmFakeRepo) {
    var reliefNote by remember { mutableStateOf("") }
    val branch = repo.currentBranch()
    CalmPage(
        title = "Good shift, ${repo.email.ifEmpty { "practitioner" }}",
        subtitle = "Clock in, see the day at a glance, answer relief without leaving home.",
    ) {
        CalmDayBanner(branchName = branch.name, day = branch.day)
        CalmSheet {
            CalmSection("Clock")
            CalmRow(label = "status", value = if (repo.clockedIn) "clocked in" else "not clocked in")
            CalmRow(label = "branch", value = branch.name)
            if (repo.clockedIn) {
                CalmGhost(label = "Clock out", onClick = { repo.clock(false) })
            } else {
                CalmPrimary(label = "Clock in", onClick = { repo.clock(true) })
            }
        }
        CalmSheet {
            CalmSection("Today · solarized ledger")
            val mine = repo.sessions.filter { it.branchId == repo.branchId }
            val pending = mine.count { it.status == CalmStatus.PENDING }
            val done = mine.count { it.status == CalmStatus.COMPLETED }
            CalmMono("pending $pending · completed $done · Branch Day ${branch.day.name}")
            CalmQuiet("Walk-ins never take NO_SHOW or CANCELLED — they simply arrive or leave the list.")
        }
        CalmSheet {
            CalmSection("Relief duty · invites · requests")
            if (repo.relief.isEmpty()) {
                CalmQuiet("No relief traffic. The floor is fully covered.")
            }
            repo.relief.forEach { item ->
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(SolBase3, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = "${item.kind.name.lowercase()} · ${item.branch}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SolBase02)
                        Text(text = if (item.answered.isEmpty()) "open" else item.answered, fontSize = 12.sp, fontFamily = SolMono, color = SolCyan)
                    }
                    Text(text = item.note, fontSize = 13.sp, color = SolBase01)
                    if (item.answered.isEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { repo.answerRelief(item.id, "accepted") }) {
                                Text("Accept", color = SolGreen, fontSize = 13.sp)
                            }
                            TextButton(onClick = { repo.answerRelief(item.id, "declined") }) {
                                Text("Decline", color = SolRed, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = reliefNote,
                onValueChange = { reliefNote = it },
                label = { Text("Ask for relief (e.g. need 18:00–21:00 cover)") },
                modifier = Modifier.fillMaxWidth(),
            )
            CalmGhost(
                label = "Send relief request",
                onClick = {
                    repo.askRelief(reliefNote)
                    reliefNote = ""
                },
            )
        }
    }
}

@Composable
fun CalmSessions(repo: CalmFakeRepo) {
    var filter by remember { mutableStateOf<CalmStatus?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var walkName by remember { mutableStateOf("") }
    var walkService by remember { mutableStateOf("") }
    val branch = repo.currentBranch()
    CalmPage(
        title = "Sessions",
        subtitle = "${branch.name} · PENDING → COMPLETED / NO_SHOW / CANCELLED. Tap a row to open void tools.",
    ) {
        CalmDayBanner(branchName = branch.name, day = branch.day)
        CalmSheet {
            CalmMono("walk-in rule: no NO_SHOW / CANCELLED for walk-ins")
            CalmQuiet("Booked sessions may end as COMPLETED, NO_SHOW, or CANCELLED. Walk-ins only arrive — void does not apply to them.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "all", CalmStatus.PENDING to "pending", CalmStatus.COMPLETED to "done", CalmStatus.NO_SHOW to "no-show", CalmStatus.CANCELLED to "voided").forEach { (status, label) ->
                val on = filter == status
                Box(
                    modifier =
                        Modifier.background(
                            if (on) SolBlue.copy(alpha = 0.16f) else SolBase2.copy(alpha = 0.6f),
                            RoundedCornerShape(6.dp),
                        )
                            .clickable { filter = status }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(text = label, fontSize = 12.sp, fontFamily = SolMono, color = if (on) SolBlue else SolBase00)
                }
            }
        }
        val rows = repo.sessions.filter { it.branchId == repo.branchId && (filter == null || it.status == filter) }
        if (rows.isEmpty()) {
            CalmSheet { CalmQuiet("Nothing under this filter. The ledger is clear.") }
        }
        rows.forEach { session ->
            val tone =
                when (session.status) {
                    CalmStatus.PENDING -> SolYellow
                    CalmStatus.COMPLETED -> SolGreen
                    CalmStatus.NO_SHOW -> SolOrange
                    CalmStatus.CANCELLED -> SolRed
                }
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(SolBase2.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .clickable { expanded = if (expanded == session.id) null else session.id }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "${session.time} · ${session.client}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SolBase02)
                        Text(text = "${session.service} · ${session.kind.name.lowercase()} · ${session.practitioner}", fontSize = 12.sp, fontFamily = SolMono, color = SolBase00)
                    }
                    CalmChip(text = session.status.name, tone = tone)
                }
                Text(text = calmMoney(session.amount), fontSize = 13.sp, fontFamily = SolMono, color = SolBase01)
                if (expanded == session.id) {
                    CalmRule()
                    if (session.voidReason.isNotEmpty()) {
                        CalmQuiet("void reason: ${session.voidReason}")
                    }
                    if (session.kind == CalmKind.WALK_IN) {
                        CalmQuiet("Walk-in: void/unvoid is hidden by rule — walk-ins never take NO_SHOW or CANCELLED.")
                    } else if (session.status == CalmStatus.CANCELLED) {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Unvoid reason (required)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CalmPrimary(
                            label = "Restore to PENDING",
                            onClick = {
                                repo.unvoidSession(session.id, reason)
                                reason = ""
                            },
                        )
                    } else {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Void reason (required)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CalmGhost(
                            label = "Void with reason",
                            onClick = {
                                repo.voidSession(session.id, reason)
                                reason = ""
                            },
                        )
                    }
                }
            }
        }
        CalmSheet {
            CalmSection("Seat a walk-in")
            OutlinedTextField(value = walkName, onValueChange = { walkName = it }, label = { Text("Guest name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = walkService, onValueChange = { walkService = it }, label = { Text("Service") }, modifier = Modifier.fillMaxWidth())
            CalmPrimary(
                label = "Seat walk-in now",
                onClick = {
                    repo.addWalkIn(walkName, walkService)
                    walkName = ""
                    walkService = ""
                },
            )
        }
    }
}

@Composable
fun CalmClients(repo: CalmFakeRepo) {
    var veiled by remember { mutableStateOf(true) }
    CalmPage(
        title = "Clients",
        subtitle = "Global across Branches. At most one PENDING session per Client — the second booking waits.",
    ) {
        CalmSheet {
            CalmMono("privacy: names veiled by default")
            CalmQuiet("At-most-one-PENDING: a Client with a PENDING session cannot open another until it resolves.")
            if (veiled) {
                CalmGhost(label = "Reveal names", onClick = { veiled = false })
            } else {
                CalmGhost(label = "Veil names", onClick = { veiled = true })
            }
        }
        repo.clients.forEach { client ->
            val display = if (veiled) "${client.code} · ●●●●●●" else "${client.code} · ${client.name}"
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(SolBase2.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = display, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SolBase02)
                    CalmMono("pending ${client.pending} · visits ${client.visits}")
                    CalmQuiet(client.note)
                    if (client.pending > 0) {
                        CalmChip(text = "1 PENDING — new booking waits", tone = SolYellow)
                    }
                }
            }
        }
    }
}

@Composable
fun CalmFinance(repo: CalmFakeRepo) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CalmDraftKind.SESSION) }
    var undoReason by remember { mutableStateOf("") }
    val branch = repo.currentBranch()
    CalmPage(
        title = "Finance · remittance",
        subtitle = "${branch.name} · SESSION drafts (net income) and PRODUCT drafts (price × quantity). Submit seals a snapshot; undo lives 48h.",
    ) {
        CalmDayBanner(branchName = branch.name, day = branch.day)
        CalmSheet {
            CalmMono("commission split: house 60 · practitioner 40")
            CalmQuiet("Commission Split note: every sealed SESSION snapshot splits 60/40 house/practitioner before the PRODUCT line is added.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalmDraftKind.entries.forEach { entry ->
                Box(
                    modifier =
                        Modifier.background(
                            if (kind == entry) SolCyan.copy(alpha = 0.18f) else SolBase2.copy(alpha = 0.6f),
                            RoundedCornerShape(6.dp),
                        )
                            .clickable { kind = entry }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(text = entry.name, fontSize = 12.sp, fontFamily = SolMono, color = if (kind == entry) SolCyan else SolBase00)
                }
            }
        }
        CalmSheet {
            CalmSection("New draft")
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            CalmPrimary(
                label = "Add ${kind.name.lowercase()} draft",
                onClick = {
                    repo.addDraft(kind, label, amount.toDoubleOrNull() ?: 0.0, 1)
                    label = ""
                    amount = ""
                },
            )
        }
        repo.drafts.forEach { draft ->
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(SolBase2.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = draft.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SolBase02)
                    CalmChip(
                        text = if (draft.undone) "UNDONE" else if (draft.submitted) "SEALED" else "DRAFT",
                        tone = if (draft.undone) SolOrange else if (draft.submitted) SolViolet else SolBlue,
                    )
                }
                CalmMono("${draft.kind.name} · ${calmMoney(draft.amount)}${if (draft.snapshot.isNotEmpty()) " · ${draft.snapshot}" else ""}")
                if (draft.undone && draft.undoReason.isNotEmpty()) {
                    CalmQuiet("undo reason: ${draft.undoReason}")
                }
                if (!draft.submitted) {
                    CalmPrimary(label = "Submit · seal snapshot", onClick = { repo.submitDraft(draft.id) })
                } else if (!draft.undone) {
                    OutlinedTextField(
                        value = undoReason,
                        onValueChange = { undoReason = it },
                        label = { Text("Undo reason (within 48h)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CalmGhost(
                        label = "Undo within 48h",
                        onClick = {
                            repo.undoDraft(draft.id, undoReason)
                            undoReason = ""
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun CalmTeam(repo: CalmFakeRepo) {
    CalmPage(
        title = "Team",
        subtitle = "Users and roles. ONBOARDING stays locked until a Capability bundle is granted.",
    ) {
        repo.mates.forEach { mate ->
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(SolBase2.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = mate.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SolBase02)
                        CalmChip(text = mate.role, tone = if (mate.locked) SolYellow else SolCyan)
                    }
                    CalmQuiet(mate.note)
                }
            }
        }
        CalmSheet {
            CalmSection("Capability glance")
            CalmRow(label = "practitioner", value = "sessions + clock")
            CalmRow(label = "coordinator", value = "roster + relief")
            CalmRow(label = "manager", value = "approvals + remittance")
            CalmRow(label = "accountant", value = "snapshots + undo")
            CalmRow(label = "onboarding", value = "locked · 0 granted")
        }
    }
}

@Composable
fun CalmMail(repo: CalmFakeRepo) {
    val unread = repo.notices.count { !it.read }
    CalmPage(
        title = "Mailbox",
        subtitle = if (unread == 0) "All calm — nothing unread." else "$unread unread — tap a card to toggle read.",
    ) {
        CalmPrimary(label = "Mark all read", onClick = { repo.markAllRead() })
        repo.notices.forEach { notice ->
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(
                            if (notice.read) SolBase2.copy(alpha = 0.45f) else SolBase3,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { repo.toggleNotice(notice.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${if (notice.read) "○" else "●"} ${notice.title}",
                            fontSize = 14.sp,
                            fontWeight = if (notice.read) FontWeight.Normal else FontWeight.SemiBold,
                            color = SolBase02,
                        )
                    }
                    Text(text = notice.body, fontSize = 13.sp, color = SolBase01)
                    CalmMono("${notice.branch} · ${notice.day}")
                }
            }
        }
    }
}

@Composable
fun CalmAudit(repo: CalmFakeRepo) {
    CalmPage(
        title = "Audit log",
        subtitle = "Every void, unvoid, submit, undo, clock, and relief answer lands here with its reason.",
    ) {
        if (repo.audits.isEmpty()) {
            CalmSheet { CalmQuiet("No entries yet — act somewhere and return.") }
        }
        repo.audits.forEach { entry ->
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(SolBase2.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "${entry.time} · ${entry.actor}", fontSize = 12.sp, fontFamily = SolMono, color = SolBase00)
                    Text(text = entry.action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SolBase02)
                    Text(text = entry.detail, fontSize = 13.sp, color = SolBase01)
                }
            }
        }
    }
}

@Composable
fun CalmProfile(
    repo: CalmFakeRepo,
    onLogout: () -> Unit,
) {
    CalmPage(
        title = "Profile",
        subtitle = "Clock, move the Branch Day, reset the demo, or log out.",
    ) {
        CalmSheet {
            CalmRow(label = "email", value = repo.email.ifEmpty { "demo@solmar.ph" })
            CalmRow(label = "clock", value = if (repo.clockedIn) "in" else "out")
            CalmRow(label = "branch day", value = repo.currentBranch().day.name)
            if (repo.clockedIn) {
                CalmGhost(label = "Clock out", onClick = { repo.clock(false) })
            } else {
                CalmPrimary(label = "Clock in", onClick = { repo.clock(true) })
            }
        }
        CalmSheet {
            CalmSection("Move the day")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalmDay.entries.forEach { day ->
                    Box(
                        modifier =
                            Modifier.background(
                                if (repo.currentBranch().day == day) SolViolet.copy(alpha = 0.18f) else SolBase3,
                                RoundedCornerShape(6.dp),
                            )
                                .clickable { repo.moveDay(day) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(text = day.name, fontSize = 12.sp, fontFamily = SolMono, color = SolBase01)
                    }
                }
            }
            CalmQuiet("The day turns at 04:00 Asia/Manila — moving it here previews OPEN / PAST / REMITTED banners.")
        }
        CalmSheet {
            CalmGhost(label = "Reset demo data", onClick = { repo.reset() })
            CalmGhost(
                label = "Log out",
                onClick = onLogout,
            )
        }
    }
}
