package com.companyb.companyapp.proto.guided_onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape

// #766 — wizard flow stations 4-8: sessions, clients, finance, team, mailbox,
// audit, profile. Each carries empty-state coaching and lights its lantern on
// the defining action.

@Composable
fun GdSessionsFlow(repo: GuidedRepo) {
    var showCreate by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val branch = repo.currentBranch()
    val items = repo.branchSessions(branch.id)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GdHeadline("Sessions — ${branch.name}")
        Spacer(Modifier.weight(1f))
        GdPrimary("+ Log session") { showCreate = true }
    }
    GdNote("A Session is one visit by one client at one branch: PENDING → COMPLETED / NO_SHOW / CANCELLED.")
    if (items.isEmpty()) {
        GdEmptyCoach("Quiet book — great teaching moment", "No sessions at this branch yet. Log one to see the PENDING flow.", "+ Log session") {
            showCreate = true
        }
    }
    items.forEach { s ->
        GdCard {
            Column(
                Modifier.fillMaxWidth().clickable { detailId = s.id },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.time, color = GdColors.LanternDeep, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(s.clientName, color = GdColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (s.walkIn) {
                        Spacer(Modifier.width(8.dp))
                        Text("WALK-IN", color = GdColors.LanternDeep, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.weight(1f))
                    GdStatusChip(s.status)
                }
                Text(
                    "${s.kind} · ₱${s.price} · ${s.practitioners}" +
                        if (s.voided) " · VOIDED (${s.voidReason})" else "",
                    color = GdColors.Faded,
                    fontSize = 12.sp,
                )
                if (s.walkIn) {
                    GdNote("Walk-in rule: cannot be marked NO_SHOW or CANCELLED — only COMPLETED is offered.")
                }
            }
        }
    }
    if (repo.sessionMoved) {
        Text("✓ Lantern lit — you moved a session.", color = GdColors.Leaf, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    } else {
        GdNote("Tap a session, move it out of PENDING, and this lantern lights.")
    }
    if (showCreate) {
        GdCreateDialog(repo, onClose = { showCreate = false })
    }
    val detail = detailId?.let { id -> repo.sessions.firstOrNull { it.id == id } }
    if (detail != null) {
        GdDetailDialog(repo, detail, onClose = { detailId = null })
    }
}

@Composable
private fun GdDetailDialog(repo: GuidedRepo, s: GdSession, onClose: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    Dialog(onCloseRequest = onClose) {
        Box(Modifier.width(560.dp).background(GdColors.Card, RoundedCornerShape(12.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GdHeadline("Session ${s.id}")
                    Spacer(Modifier.weight(1f))
                    GdLink("Close") { onClose() }
                }
                GdStatusChip(s.status)
                Text("${s.time} · ${s.clientName} · ${s.kind}", color = GdColors.Ink, fontSize = 14.sp)
                Text(
                    "Final price ₱${s.price} (defaults to base rate, overridable) · Practitioners: ${s.practitioners}",
                    color = GdColors.Faded,
                    fontSize = 12.sp,
                )
                GdSubhead("Status transitions")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GdPrimary("Completed", enabled = s.status == GdSessionStatus.PENDING && !s.voided) {
                        repo.advance(s.id, GdSessionStatus.COMPLETED)
                    }
                    GdGhost(
                        "No-show",
                        enabled = s.status == GdSessionStatus.PENDING && !s.walkIn && !s.voided,
                        onClick = { repo.advance(s.id, GdSessionStatus.NO_SHOW) },
                    )
                    GdGhost(
                        "Cancel",
                        enabled = s.status == GdSessionStatus.PENDING && !s.walkIn && !s.voided,
                        onClick = { repo.advance(s.id, GdSessionStatus.CANCELLED) },
                    )
                }
                GdSubhead("Void / unvoid")
                if (s.voided) {
                    Text("Voided: ${s.voidReason}", color = GdColors.Alarm, fontSize = 13.sp)
                    GdGhost("Unvoid — restore record") { repo.unvoidSession(s.id) }
                } else {
                    TextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Void reason (required)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    GdGhost("Void with reason", enabled = reason.isNotBlank()) {
                        repo.voidSession(s.id, reason.trim())
                        reason = ""
                    }
                }
                GdNote("Void excludes the session from finance but preserves the record — never a delete.")
            }
        }
    }
}

@Composable
private fun GdCreateDialog(repo: GuidedRepo, onClose: () -> Unit) {
    var time by remember { mutableStateOf("16:30") }
    var client by remember { mutableStateOf("New Client") }
    var price by remember { mutableStateOf("1200") }
    var walkIn by remember { mutableStateOf(false) }
    Dialog(onCloseRequest = onClose) {
        Box(Modifier.width(520.dp).background(GdColors.Card, RoundedCornerShape(12.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GdHeadline("Log session")
                    Spacer(Modifier.weight(1f))
                    GdLink("Close") { onClose() }
                }
                TextField(value = time, onValueChange = { time = it }, label = { Text("Time (HH:MM)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextField(value = client, onValueChange = { client = it }, label = { Text("Client") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextField(value = price, onValueChange = { price = it.filter(Char::isDigit) }, label = { Text("Price (PHP)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = walkIn, onCheckedChange = { walkIn = it })
                    Spacer(Modifier.width(6.dp))
                    Text("Walk-in (no NO_SHOW / CANCELLED allowed)", color = GdColors.Ink, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GdPrimary("Create PENDING") {
                        repo.addSession(
                            time.ifBlank { "17:00" },
                            client.ifBlank { "New Client" },
                            repo.currentBranchId,
                            if (walkIn) "Walk-in" else "Follow-up",
                            walkIn,
                            price.toIntOrNull() ?: 1200,
                        )
                        onClose()
                    }
                    GdGhost("Cancel") { onClose() }
                }
            }
        }
    }
}

@Composable
fun GdClientsFlow(repo: GuidedRepo) {
    var showAnon by remember { mutableStateOf(false) }
    GdHeadline("Clients — one global record")
    GdNote("A Client is global across branches. At most one PENDING session per client at a time — booking a second is blocked.")
    if (showAnon) repo.anonViewed = true
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = showAnon,
            onCheckedChange = {
                showAnon = it
                if (it) {
                    repo.anonViewed = true
                    repo.log("${repo.currentUser?.name} previewed the anonymized client view")
                }
            },
        )
        Text("Show anonymized view", color = GdColors.Ink, fontSize = 13.sp)
    }
    repo.clients.forEach { c ->
        GdCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showAnon || c.anonymized) "Client ${c.id.uppercase()}" else c.name,
                        color = GdColors.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (c.pendingCount > 0) "1 PENDING — booking blocked" else "no PENDING",
                        color = if (c.pendingCount > 0) GdColors.Alarm else GdColors.Leaf,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (showAnon || c.anonymized) {
                    Text(
                        if (c.anonymized) "Anonymized: PII nullified, retained F · 42 for reporting" else "Anonymized preview: names hidden, counts kept",
                        color = GdColors.Faded,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(c.contact, color = GdColors.Faded, fontSize = 12.sp)
                }
            }
        }
    }
    if (repo.anonViewed) {
        Text("✓ Lantern lit — you previewed anonymized reporting.", color = GdColors.Leaf, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    } else {
        GdNote("Tick the anonymized view to light this lantern — reporting keeps gender and age only.")
    }
}

@Composable
fun GdFinanceFlow(repo: GuidedRepo) {
    GdHeadline("Finance & remittance")
    GdNote("Two independent flows: SESSION (net income after compensation and expenses) and PRODUCT (unit price × quantity). Submitting seals an immutable snapshot; Undo reopens it within 48h with a reason.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GdPrimary("+ Draft SESSION") { repo.newDraft("SESSION") }
        GdGhost("+ Draft PRODUCT") { repo.newDraft("PRODUCT") }
    }
    repo.remittances.forEach { r ->
        GdCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.flow} ${r.id}", color = GdColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        r.stage.uppercase(),
                        color = if (r.stage == "Snapshot") GdColors.River else GdColors.LanternDeep,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("₱${r.amount}", color = GdColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${r.branchName}" + (r.submittedHoursAgo?.let { " · submitted ${it}h ago" } ?: " · not yet submitted"),
                    color = GdColors.Faded,
                    fontSize = 12.sp,
                )
                if (r.stage == "Snapshot" && r.id == "r-03") {
                    GdNote("Submitted 72h ago — past the Undo window. This snapshot is permanent.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.stage == "Draft") {
                        GdPrimary("Submit → snapshot") { repo.submitRemittance(r.id) }
                    } else if (r.id != "r-03") {
                        GdGhost("Undo (48h, reason: coaching demo)") { repo.undoRemittance(r.id, "coaching demo") }
                    }
                }
            }
        }
    }
    GdCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GdSubhead("Commission split coach")
            Text(
                "Product commissions pool per branch day and split equally among all practitioners " +
                    "and coordinators clocked in at the sold_at time. Manual inclusions/exclusions can " +
                    "override. Separate from compensation, never remitted.",
                color = GdColors.Ink,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
    if (repo.financeTouched) {
        Text("✓ Lantern lit — you submitted or undid a remittance.", color = GdColors.Leaf, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    } else {
        GdNote("Submit a draft (or undo r-01 within its 48h window) to light this lantern.")
    }
}

@Composable
fun GdTeamFlow(repo: GuidedRepo) {
    GdHeadline("Team & relief board")
    GdNote("Branch Slot orders the display (1 = senior); relief practitioners sort after home slots. Deactivate blocks login instantly but keeps records; Reactivate restores via a fresh login.")
    repo.members.forEach { m ->
        GdCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, color = GdColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${m.role} · slot ${m.slot} · home ${m.home}" + if (m.relief) " · relief today" else "",
                        color = GdColors.Faded,
                        fontSize = 12.sp,
                    )
                }
                GdGhost("Deactivate") { repo.log("${m.name} deactivated (fake) — login blocked, records kept") }
            }
        }
    }
    GdCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GdSubhead("Relief board")
            repo.reliefBoard.forEach {
                Text("• $it", color = GdColors.Ink, fontSize = 13.sp)
            }
            GdNote("Requests are outsider-initiated broadcasts; invites are branch-initiated single-day offers.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GdPrimary("Accept Saturday invite") { repo.log("Relief invite accepted — day grant written (fake)") }
                GdGhost("Grant a request") { repo.log("Relief request granted by ${repo.currentUser?.name} (fake)") }
            }
        }
    }
}

@Composable
fun GdMailboxFlow(repo: GuidedRepo) {
    val unread = repo.notices.count { !it.read }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GdHeadline("Mailbox" + if (unread > 0) " ($unread unread)" else "")
        Spacer(Modifier.weight(1f))
        GdGhost("Mark all read") { repo.markAllRead() }
    }
    if (repo.notices.isEmpty()) {
        GdEmptyCoach("All caught up", "Read rows are kept forever as history — this empty state means focus time.", "Send a test notice") {
            repo.notices.add(0, GdNotice("n-fake-${repo.noticeSeq}", "Coaching nudge", "You asked for mail and the guide obliged.", false))
        }
    }
    repo.notices.forEach { n ->
        GdCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        (if (n.read) "" else "● ") + n.title,
                        color = if (n.read) GdColors.Faded else GdColors.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(n.body, color = GdColors.Faded, fontSize = 12.sp)
                }
                if (!n.read) {
                    GdGhost("Mark read") {
                        n.read = true
                        val copy = repo.notices.toList()
                        repo.notices.clear()
                        repo.notices.addAll(copy)
                    }
                }
            }
        }
    }
}

@Composable
fun GdAuditFlow(repo: GuidedRepo) {
    GdHeadline("Audit log")
    GdNote("Newest first. Every wizard action above appended an entry here — the trail is the proof of the day.")
    GdCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repo.audit.forEach {
                Text("• $it", color = GdColors.Ink, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun GdProfileFlow(repo: GuidedRepo, onBack: () -> Unit) {
    val u = repo.currentUser
    GdHeadline("Profile")
    GdCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(u?.name ?: "—", color = GdColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Role: ${u?.role}", color = GdColors.Ink, fontSize = 13.sp)
            Text(
                "Capabilities: ${u?.capabilities?.joinToString() ?: "none"}",
                color = GdColors.Faded,
                fontSize = 12.sp,
            )
            Text(
                if (repo.clockedIn) "Clocked in at ${repo.currentBranch().name}" else "Off the clock",
                color = GdColors.Faded,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn) {
                    GdGhost("Clock out") { repo.clockOut() }
                }
                GdGhost("Logout") {
                    repo.clockOut()
                    repo.log("${u?.name} signed out")
                    repo.currentUser = null
                }
                GdGhost("Exit prototype") { onBack() }
            }
        }
    }
}
